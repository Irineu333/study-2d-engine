import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.kotlinJvm)
}

private val osArch: String = run {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").orEmpty()
    val isAarch64 = arch == "aarch64" || arch == "arm64"
    when {
        os.isMacOsX && isAarch64 -> "macos-arm64"
        os.isMacOsX -> "macos-x64"
        os.isWindows -> "windows-x64"
        else -> "linux-x64"
    }
}

dependencies {
    implementation(projects.engine)
    api(libs.skiko.awt)
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$osArch:${libs.versions.skiko.get()}")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
