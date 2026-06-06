import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.kotlinJvm)
}

private val nativesClassifier: String = run {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").orEmpty()
    val isAarch64 = arch == "aarch64" || arch == "arm64"
    when {
        os.isMacOsX && isAarch64 -> "natives-macos-arm64"
        os.isMacOsX -> "natives-macos"
        os.isWindows -> "natives-windows"
        else -> "natives-linux"
    }
}

private val lwjglVersion = libs.versions.lwjgl.get()

dependencies {
    implementation(projects.engine)
    // Host-agnostic JDK audio backend — the same module serves SkikoHost too,
    // proving render and audio are independent axes (no OpenAL needed in v1).
    implementation(projects.engineAudioJavasound)

    api(libs.lwjgl.core)
    api(libs.lwjgl.glfw)
    api(libs.lwjgl.opengl)
    api(libs.lwjgl.nanovg)

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$nativesClassifier")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$nativesClassifier")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$nativesClassifier")
    runtimeOnly("org.lwjgl:lwjgl-nanovg:$lwjglVersion:$nativesClassifier")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
