package com.controlqr.acceso.sync

import android.content.Context
import android.util.Log
import com.controlqr.acceso.data.AccessRepository
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.data.db.PassOrigin
import com.controlqr.acceso.data.db.PassStatus
import com.controlqr.acceso.data.db.ScanEventEntity
import com.controlqr.acceso.data.db.ScanType
import com.controlqr.acceso.data.prefs.AppSettings
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Lo que la interfaz necesita saber del estado de la nube. */
enum class SyncState {
    /** El APK se compiló sin google-services.json. */
    NO_CONFIGURADA,

    /** El usuario la apagó en Ajustes. */
    APAGADA,

    CONECTANDO,

    /** Al día con el servidor. */
    EN_LINEA,

    /** Sin señal: se sigue operando y los cambios quedan encolados en el teléfono. */
    SIN_CONEXION
}

data class SyncStatus(
    val state: SyncState = SyncState.NO_CONFIGURADA,
    val pendingWrites: Int = 0,
    val lastSyncAt: Long = 0L,
    val detail: String = ""
)

/**
 * Réplica en tiempo real entre los equipos de un mismo sitio.
 *
 * Regla de diseño: **la nube nunca decide un acceso**. Cuando alguien escanea, la
 * respuesta sale de la base local en milisegundos y sin consultar la red; esto solo
 * propaga el resultado a los demás equipos. Si Firestore no está disponible, la caseta
 * opera exactamente igual que antes.
 *
 * Firestore mantiene su propia cola en disco: lo escrito sin señal sube solo al volver
 * la conexión, sin que la app tenga que implementar reintentos.
 */
class CloudSync(
    private val context: Context,
    private val repository: AccessRepository,
    private val settings: AppSettings
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var firestore: FirebaseFirestore? = null
    private var passListener: ListenerRegistration? = null
    private var eventListener: ListenerRegistration? = null
    private var started = false

    /** True solo si el APK se compiló con la configuración de Firebase incluida. */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    // ------------------------------------------------------------------ arranque

    fun start() {
        if (started) return

        if (!isConfigured) {
            _status.value = SyncStatus(
                state = SyncState.NO_CONFIGURADA,
                detail = "Esta versión de la app se compiló sin credenciales de Firebase."
            )
            return
        }
        if (!settings.syncEnabled) {
            _status.value = SyncStatus(SyncState.APAGADA, detail = "Sincronización desactivada en Ajustes.")
            return
        }
        if (settings.siteId.isBlank()) {
            _status.value = SyncStatus(
                state = SyncState.NO_CONFIGURADA,
                detail = "Este equipo aún no pertenece a un sitio."
            )
            return
        }

        started = true
        _status.value = SyncStatus(SyncState.CONECTANDO, lastSyncAt = settings.lastSyncAt)

        scope.launch {
            runCatching {
                val db = FirebaseFirestore.getInstance().apply {
                    firestoreSettings = FirebaseFirestoreSettings.Builder()
                        // Caché en disco: es lo que permite operar sin señal.
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                }
                firestore = db

                // Identidad anónima: no pedimos correo ni contraseña al vigilante,
                // pero las reglas de Firestore sí pueden exigir sesión iniciada.
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) auth.signInAnonymously().await()

                attachListeners(db)
                pushPendingLocal()
            }.onFailure { error ->
                started = false
                Log.w(TAG, "No se pudo iniciar la sincronización", error)
                _status.value = SyncStatus(
                    state = SyncState.SIN_CONEXION,
                    lastSyncAt = settings.lastSyncAt,
                    detail = error.message ?: "Sin conexión con el servidor."
                )
            }
        }
    }

    fun stop() {
        passListener?.remove()
        eventListener?.remove()
        passListener = null
        eventListener = null
        started = false
        _status.value = _status.value.copy(state = SyncState.APAGADA)
    }

    fun restart() {
        stop()
        start()
    }

    // ------------------------------------------------------------------ escucha

    private fun attachListeners(db: FirebaseFirestore) {
        val site = db.collection(SITES).document(settings.siteId)

        passListener = site.collection(PASSES).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _status.value = _status.value.copy(
                    state = SyncState.SIN_CONEXION,
                    detail = error.message ?: "Se perdió la conexión."
                )
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            // hasPendingWrites indica cambios locales que aún no confirma el servidor.
            val pending = snapshot.documents.count { it.metadata.hasPendingWrites }
            val fromCache = snapshot.metadata.isFromCache

            val remotePasses = snapshot.documents.mapNotNull { it.toPassEntity() }
            if (remotePasses.isNotEmpty()) {
                scope.launch {
                    // Se reutilizan las mismas reglas de fusión del respaldo por archivo:
                    // una entrada o una salida registrada nunca se pierde.
                    repository.mergePasses(remotePasses)
                    settings.lastSyncAt = System.currentTimeMillis()
                }
            }

            _status.value = SyncStatus(
                state = if (fromCache) SyncState.SIN_CONEXION else SyncState.EN_LINEA,
                pendingWrites = pending,
                lastSyncAt = settings.lastSyncAt,
                detail = if (fromCache) "Mostrando datos locales; se subirán al recuperar señal." else ""
            )
        }

        eventListener = site.collection(EVENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val remoteEvents = snapshot.documentChanges
                    .filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    .mapNotNull { it.document.toScanEvent() }
                    .filter { it.deviceCode != settings.deviceCode }

                if (remoteEvents.isNotEmpty()) {
                    scope.launch { repository.mergeEvents(remoteEvents) }
                }
            }
    }

    // ------------------------------------------------------------------- subida

    /** Se llama tras emitir un pase o registrar una entrada o salida. */
    fun push(pass: PassEntity) {
        val db = firestore ?: return
        if (settings.siteId.isBlank()) return

        db.collection(SITES).document(settings.siteId)
            .collection(PASSES).document(pass.tokenId.toString())
            .set(pass.toMap(), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "No se pudo subir el pase ${pass.folio}", it) }
    }

    fun push(event: ScanEventEntity) {
        val db = firestore ?: return
        if (settings.siteId.isBlank()) return

        val id = "${settings.deviceCode}-${event.at}-${event.tokenId ?: 0}"
        db.collection(SITES).document(settings.siteId)
            .collection(EVENTS).document(id)
            .set(event.toMap(), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "No se pudo subir el evento", it) }
    }

    /**
     * Sube lo que se registró mientras el equipo no estaba sincronizando.
     * Firestore deduplica por id de documento, así que reenviar es inofensivo.
     */
    private fun pushPendingLocal() {
        scope.launch {
            runCatching {
                repository.allPasses().forEach { push(it) }
            }.onFailure { Log.w(TAG, "No se pudo subir el histórico local", it) }
        }
    }

    // -------------------------------------------------------------- conversión

    private fun PassEntity.toMap(): Map<String, Any?> = mapOf(
        "tokenId" to tokenId,
        "folio" to folio,
        "keyId" to keyId,
        "nonce" to nonce,
        "fullName" to fullName,
        "carrier" to carrier,
        "plate" to plate,
        "company" to company,
        "notes" to notes,
        "issuedBy" to issuedBy,
        "issuedAt" to issuedAt,
        "validFrom" to validFrom,
        "validUntil" to validUntil,
        "status" to status.name,
        "entryAt" to entryAt,
        "entryBy" to entryBy,
        "entryDevice" to entryDevice,
        "exitAt" to exitAt,
        "exitBy" to exitBy,
        "exitDevice" to exitDevice,
        "revokedAt" to revokedAt,
        "revokedReason" to revokedReason,
        "payload" to payload,
        "updatedAt" to updatedAt
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toPassEntity(): PassEntity? = runCatching {
        val tokenId = getLong("tokenId")?.toInt() ?: return null
        PassEntity(
            tokenId = tokenId,
            folio = getString("folio").orEmpty(),
            keyId = getLong("keyId")?.toInt() ?: 1,
            nonce = getLong("nonce")?.toInt() ?: 0,
            fullName = getString("fullName").orEmpty(),
            carrier = getString("carrier").orEmpty(),
            plate = getString("plate").orEmpty(),
            company = getString("company").orEmpty(),
            notes = getString("notes").orEmpty(),
            issuedBy = getString("issuedBy").orEmpty(),
            issuedAt = getLong("issuedAt") ?: 0L,
            validFrom = getLong("validFrom") ?: 0L,
            validUntil = getLong("validUntil") ?: 0L,
            status = runCatching { PassStatus.valueOf(getString("status").orEmpty()) }
                .getOrDefault(PassStatus.EMITIDO),
            origin = PassOrigin.IMPORTADO,
            entryAt = getLong("entryAt"),
            entryBy = getString("entryBy"),
            entryDevice = getLong("entryDevice")?.toInt(),
            exitAt = getLong("exitAt"),
            exitBy = getString("exitBy"),
            exitDevice = getLong("exitDevice")?.toInt(),
            revokedAt = getLong("revokedAt"),
            revokedReason = getString("revokedReason"),
            payload = getString("payload").orEmpty(),
            updatedAt = getLong("updatedAt") ?: 0L
        )
    }.getOrNull()

    private fun ScanEventEntity.toMap(): Map<String, Any?> = mapOf(
        "tokenId" to tokenId,
        "folio" to folio,
        "fullName" to fullName,
        "type" to type.name,
        "at" to at,
        "byUser" to byUser,
        "deviceCode" to deviceCode,
        "accepted" to accepted,
        "message" to message
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toScanEvent(): ScanEventEntity? = runCatching {
        ScanEventEntity(
            tokenId = getLong("tokenId")?.toInt(),
            folio = getString("folio"),
            fullName = getString("fullName"),
            type = runCatching { ScanType.valueOf(getString("type").orEmpty()) }
                .getOrDefault(ScanType.DENEGADO),
            at = getLong("at") ?: 0L,
            byUser = getString("byUser").orEmpty(),
            deviceCode = getLong("deviceCode")?.toInt() ?: 0,
            accepted = getBoolean("accepted") ?: false,
            message = getString("message").orEmpty()
        )
    }.getOrNull()

    companion object {
        private const val TAG = "CloudSync"
        private const val SITES = "sitios"
        private const val PASSES = "pases"
        private const val EVENTS = "eventos"
    }
}
