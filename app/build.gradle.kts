import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// La sincronización en la nube es opcional. Sin google-services.json el plugin no se
// aplica, no se genera la configuración de Firebase y la app arranca en modo solo local:
// así el repositorio compila para cualquiera que lo clone sin tener el proyecto de Firebase.
val firebaseConfig = file("google-services.json")
val cloudSyncAvailable = firebaseConfig.exists()
if (cloudSyncAvailable) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("google-services.json no encontrado: se compila sin sincronización en la nube.")
}

// Firma de release: si existe keystore.properties (local) o las variables de entorno
// que inyecta GitHub Actions, se firma con la llave real. Si no, se usa la de debug
// para que el proyecto siempre compile.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun secret(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val storeFilePath = secret("storeFile", "KEYSTORE_FILE")
val hasReleaseKeystore = storeFilePath != null && rootProject.file(storeFilePath).exists()

android {
    namespace = "com.controlqr.acceso"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.controlqr.acceso"
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        // Permite que la interfaz explique por qué la sincronización no está disponible
        // en lugar de mostrar un interruptor que no hace nada.
        buildConfigField("boolean", "CLOUD_SYNC_AVAILABLE", cloudSyncAvailable.toString())
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = secret("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "KEY_ALIAS")
                keyPassword = secret("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    // CameraX expone ListenableFuture en su API. Firebase arrastra el artefacto
    // "listenablefuture:9999.0-empty-to-avoid-conflict-with-guava", que está vacío a
    // propósito y gana la resolución, dejando esa clase sin implementación. Declarar
    // Guava explícitamente devuelve la clase real al classpath.
    implementation(libs.guava)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    // Sincronización en tiempo real. Firestore trae cola offline propia: las escrituras
    // hechas sin señal se guardan en el teléfono y suben solas al recuperar la conexión.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
