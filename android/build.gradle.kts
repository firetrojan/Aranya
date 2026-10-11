// Aranya root build configuration.
//
// Single module on purpose. Stage 01 has one app and a Python data pipeline; splitting
// into Gradle modules before there is a second Android component would add ceremony
// with no benefit. Module boundaries are introduced in a later stage only when there
// is real separation to enforce.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
