plugins {
    kotlin("jvm") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
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
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    
    // Database driver for testing
    implementation("com.h2database:h2:2.2.224")
    
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