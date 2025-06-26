plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

group = "org.jetbrains"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(projects.annotations)
    implementation(libs.ksp.api)
    implementation(libs.exposed.jdbc)
    implementation(libs.kotlinpoet.ksp)
}