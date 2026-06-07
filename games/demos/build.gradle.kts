import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineSkiko)
    implementation(projects.engineLwjgl)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

application {
    mainClass.set("com.neoutils.engine.games.demos.MainKt")
}

tasks.register<JavaExec>("runLwjgl") {
    group = "application"
    description = "Runs :games:demos using the LWJGL backend"
    mainClass.set("com.neoutils.engine.games.demos.MainLwjglKt")
    classpath = sourceSets["main"].runtimeClasspath
    // macOS requires the AppKit main thread for GLFW/NSApp. The Skiko entry
    // point goes through AWT and escapes this constraint; LWJGL does not.
    if (OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
