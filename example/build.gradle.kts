plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    application
}

dependencies {
    implementation(projects.annotations)
    ksp(project(":generator"))
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.postgresql)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDirs("build/generated/ksp/main/kotlin")
    }
}

tasks.processResources.get().dependsOn("kspKotlin")
