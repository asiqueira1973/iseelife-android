// build.gradle.kts (raiz) — declara os plugins do projeto sem aplicá-los aqui (épico 8.0).
// Cada módulo (app) aplica o que precisa. Versões vêm do version catalog (libs.versions.toml).

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.ksp) apply false
}
