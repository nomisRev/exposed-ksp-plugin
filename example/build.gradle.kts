plugins {
    kotlin("jvm") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
    application
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Use the processor from the parent project
    ksp(project(":"))

    // Need the annotation at compile time
    implementation(project(":"))

    // Exposed
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")

    // Database drivers
    implementation("com.h2database:h2:2.2.224")
    implementation("org.postgresql:postgresql:42.7.2")

    // TestContainers
    implementation("org.testcontainers:testcontainers:1.19.7")
    implementation("org.testcontainers:postgresql:1.19.7")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("SimpleExampleKt")
}

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

tasks.named("run") {
    dependsOn("kspKotlin")
}
