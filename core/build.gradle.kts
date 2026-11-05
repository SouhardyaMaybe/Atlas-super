plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.atlas.super"
version = "0.1.0"

// Compile to JVM 17 bytecode (AGP 8.x-compatible). The compiler runs on whatever JDK
// builds the project (JDK 17 in CI, JDK 21 locally); no toolchain download required.
repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnitPlatform()
}
