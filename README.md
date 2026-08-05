# Control QR · Control de accesos por código QR

Aplicación Android para controlar entradas y salidas de personal y transportistas mediante
códigos QR de un solo ciclo: **un pase sirve exactamente una vez para entrar y una vez para
salir**. Al tercer escaneo se rechaza.

Funciona **sin conexión a internet**. La red solo se usa para descargar actualizaciones.

---

## Cómo funciona

### El QR es autocontenido y va firmado

El código no es un número que haya que consultar en un servidor. Lleva dentro los datos
del visitante y una **firma HMAC-SHA256** hecha con la llave secreta del sitio:

```
CQR1.<datos + firma, en Base64URL>
        │
        ├─ folio (equipo + serie)
        ├─ nombre completo
        ├─ línea transportista
        ├─ placas / unidad, empresa, observaciones
        ├─ fecha y hora de ingreso autorizada, vigencia
        └─ firma de 12 bytes
```

El teléfono del vigilante verifica la firma con su copia de la llave. Si el código fue
inventado o alterado, la firma no cuadra y se rechaza — **sin necesidad de red ni de base de
datos compartida**. Eso es lo que permite operar en una caseta aislada.

### Bolsa de folios

Cada equipo master tiene una bolsa (10 000 por omisión). Emitir un pase consume un folio
correlativo. El folio se compone de `código de equipo` + `serie`, así que dos masters con
códigos distintos nunca producen folios repetidos. La pantalla principal muestra cuántos
quedan; si la emisión falla, el folio no se cobra.

### Ciclo de vida del pase

```
EMITIDO ──escaneo de entrada──> DENTRO ──escaneo de salida──> COMPLETADO
   │                                                              │
   └──── el master puede CANCELARLO en cualquier momento ─────────┘
```

- **Tercer escaneo:** rechazado, con el motivo en pantalla.
- **Pase vencido:** no deja entrar. **Sí deja salir** — quien ya está adentro siempre puede irse.
- **Pase cancelado:** no deja entrar. Si la persona ya estaba adentro, la salida se permite
  y queda marcada en la bitácora.

---

## Roles

| | Master | Vasallo |
|---|---|---|
| Emitir pases QR | ✅ | ❌ |
| Escanear entrada y salida | ✅ | ✅ |
| Ver reportes y ocupación | ✅ | ❌ |
| Historial completo y cancelar pases | ✅ | ❌ |
| Administrar usuarios | ✅ | ❌ |
| Ajustes y respaldos | ✅ | ❌ |

El vasallo solo ve dos botones: **ESCANEAR ENTRADA** y **ESCANEAR SALIDA**. Las rutas del
master ni siquiera existen en su grafo de navegación.

---

## Puesta en marcha

### 1. Teléfono del master

1. Instala el APK y abre la app.
2. Llena **Configuración inicial**: nombre del sitio, código de equipo (`1`), tamaño de la
   bolsa (`10000`) y la cuenta master.
3. Al guardar se genera la llave del sitio. **Anota la contraseña en un lugar seguro.**

### 2. Teléfono de cada vigilante

1. En el master: **Usuarios → Nuevo vasallo** (nombre, usuario, PIN temporal).
2. Asigna un **código de equipo distinto** a cada teléfono (2, 3, 4…) y genera el QR de
   vinculación.
3. En el teléfono del vigilante: instala el APK, toca **«Este equipo es de un vigilante»** y
   escanea ese QR.
4. El vigilante entra con su usuario y el PIN, y la app le pide definir su propia contraseña.

> ⚠️ El QR de vinculación **contiene la llave del sitio**. Muéstralo en persona, una sola vez.
> No lo mandes por WhatsApp ni lo dejes fotografiado.

### 3. Operación diaria

- El master genera el pase y lo envía o imprime con **Enviar o imprimir credencial**.
- En la caseta se escanea en **ENTRADA** al llegar y en **SALIDA** al irse.
- La app avisa con tono y vibración: el vigilante no necesita mirar la pantalla.

---

## Sincronización en tiempo real (opcional)

El master puede ver los escaneos de las casetas conforme ocurren, sobre datos móviles o wifi.

**La validación del QR nunca depende de la red.** Cuando el vigilante escanea, la respuesta
sale de la base local en milisegundos. La sincronización solo replica el resultado hacia los
demás equipos.

```
Vasallo escanea → base local (decide entrada/salida al instante)
                       ↓
                  Firestore (encola si no hay señal)
                       ↓
Master: Firestore → base local → el tablero se actualiza solo
```

Sin señal la caseta opera igual y los movimientos suben solos al recuperar conexión. El
indicador de estado (**En línea / Sin señal / N pendientes**) está siempre visible en el
tablero y en las pantallas de caseta.

### Configurarla

1. Crea un proyecto en **https://console.firebase.google.com**
2. Agrega una app Android con el paquete **`com.controlqr.acceso`**
3. Descarga **`google-services.json`** y colócalo en la carpeta **`app/`**
4. **Firestore Database → Crear base de datos** (modo producción)
5. **Firestore Database → Reglas**: pega el contenido de [`firestore.rules`](firestore.rules) y publica
6. **Authentication → Sign-in method**: activa **Anónimo**
7. Sube el cambio y publica una versión nueva

Sin `google-services.json` el proyecto **compila igual** y la app funciona 100 % offline; en
Ajustes aparece el aviso de que esa versión se compiló sin credenciales.

> `google-services.json` no es un secreto: va dentro del APK y cualquiera puede extraerlo.
> La seguridad la dan las reglas de Firestore, no ocultar ese archivo. Por eso se versiona.

### Lo que la sincronización no resuelve

Si el equipo de entrada y el de salida están **ambos sin señal al mismo tiempo**, no pueden
consultarse entre sí: el uso único sigue siendo local y de consistencia eventual. La
sincronización reduce mucho esa ventana, no la elimina. Para cerrarla del todo habría que
exigir conexión en el momento del escaneo, lo que rompería la operación offline.

### Privacidad

Al activarla, los nombres, líneas transportistas y placas salen del teléfono hacia servidores
de Google. Lee el alcance de las reglas al final de [`firestore.rules`](firestore.rules) antes
de encenderla si manejas datos sujetos a normativa.

---

## Reportes

- **Ocupación en tiempo real:** cuántas personas están adentro en este momento.
- **QR activos por día:** pases cuya vigencia cubre ese día.
- **Entradas y salidas** por día (14 días), semana (8 semanas) y mes (12 meses), con gráfica
  comparativa y tabla.
- **Permanencia media** y pendientes (entradas sin salida).
- **Bitácora completa**, incluidos los intentos rechazados y por qué se rechazaron.
- Exportación a **CSV** (abre directo en Excel, con acentos) y respaldo **JSON**.

### Entrada y salida en teléfonos distintos

Si la caseta de entrada y la de salida son equipos diferentes, cada uno tiene solo su mitad
de la historia. Dos mecanismos lo resuelven:

1. **«Permitir salida sin entrada local»** (activo por omisión): el equipo de salida acepta el
   pase y lo marca para conciliar.
2. **Exportar / importar respaldo:** cada equipo exporta su `.json` y el master lo importa.
   La fusión nunca borra información: si a un registro le falta la entrada y al otro la salida,
   el resultado tiene ambas.

---

## Publicar en GitHub y generar el link de descarga

### Primera vez

```bash
git init && git add . && git commit -m "Control QR: versión inicial"
```

Crea el repositorio y súbelo:

```bash
gh repo create Dace077/ControlQR --public --source=. --push
```

### Publicar una versión

```bash
git tag v1.0.0 && git push origin v1.0.0
```

GitHub Actions compila el APK y crea un **Release** con el archivo adjunto. Ese es el link de
descarga que compartes. Para la siguiente versión, `v1.0.1`, y así.

### Firma del APK (recomendado)

Sin esto el APK se firma con la llave de depuración: sirve para probar, pero **las
actualizaciones futuras no se podrán instalar encima**. Genera tu llave una sola vez:

```bash
keytool -genkeypair -v -keystore controlqr.jks -keyalg RSA -keysize 2048 -validity 10000 -alias controlqr
```

Conviértela a texto:

```bash
base64 -w0 controlqr.jks > controlqr.b64
```

En GitHub → *Settings → Secrets and variables → Actions*, crea:

| Secreto | Contenido |
|---|---|
| `KEYSTORE_BASE64` | el contenido de `controlqr.b64` |
| `KEYSTORE_PASSWORD` | la contraseña del almacén |
| `KEY_ALIAS` | `controlqr` |
| `KEY_PASSWORD` | la contraseña de la llave |

> Guarda `controlqr.jks` fuera del repositorio y respáldalo. Si lo pierdes, no podrás publicar
> actualizaciones que se instalen sobre la versión ya distribuida.

### Actualizaciones desde la app

**Ajustes → Buscar actualización** consulta el último Release del repositorio y ofrece
descargar el APK. Es lo único que usa internet.

---

## Compilar localmente (opcional)

Requiere JDK 17 y el SDK de Android 35. Con Android Studio basta abrir la carpeta.
Desde la terminal, si tienes Gradle instalado:

```bash
gradle wrapper && ./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`.

Pruebas unitarias del códec y la criptografía:

```bash
gradle testDebugUnitTest
```

---

## Estructura

```
app/src/main/java/com/controlqr/acceso/
├── core/
│   ├── crypto/Crypto.kt          HMAC, PBKDF2, Base64URL
│   ├── qr/PassPayload.kt         formato binario del pase y su firma
│   ├── qr/ProvisioningPayload.kt QR de vinculación de equipos
│   ├── qr/QrRenderer.kt          generación del QR y de la credencial
│   └── Formats.kt                fechas y cortes de calendario en hora local
├── data/
│   ├── db/                       Room: usuarios, pases, bitácora
│   ├── prefs/AppSettings.kt      llave del sitio (cifrada) y bolsa de folios
│   ├── AccessRepository.kt       reglas de entrada/salida y anti-reuso
│   ├── UserRepository.kt         cuentas, sesión y aprovisionamiento
│   ├── Reports.kt                agregación por día, semana y mes
│   └── BackupManager.kt          exportar/importar JSON y CSV
├── sync/CloudSync.kt             réplica en tiempo real entre equipos (opcional)
├── ui/
│   ├── auth/                     alta inicial, login, vinculación
│   ├── guard/                    pantallas de caseta (entrada y salida)
│   ├── master/                   tablero, emisión, historial, reportes, usuarios, ajustes
│   ├── components/               cámara, QR, tarjetas, avisos
│   └── vm/                       ViewModels
└── update/UpdateChecker.kt       consulta de Releases de GitHub
```

---

## Alcance de la seguridad

Lo que la firma **sí** garantiza: nadie puede fabricar un pase válido sin la llave del sitio,
ni alterar el nombre o la vigencia de uno existente.

Lo que **no** garantiza: un QR legítimo se puede fotografiar y reenviar. Se mitiga con el uso
único, la vigencia corta y el registro de quién escaneó cada movimiento — pero si el control
tiene que resistir a alguien que comparte su pase deliberadamente, hay que agregar validación
de identidad en la caseta (identificación oficial o foto en el registro de entrada).

La llave del sitio se guarda en `EncryptedSharedPreferences`. En equipos cuyo keystore de
hardware esté dañado, la app cae a almacenamiento privado sin cifrar en lugar de quedar
inservible.
