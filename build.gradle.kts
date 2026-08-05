plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // Se deja en el classpath pero sin aplicar: el módulo :app lo activa solo si
    // existe google-services.json, para que el proyecto compile sin credenciales.
    alias(libs.plugins.google.services) apply false
}
