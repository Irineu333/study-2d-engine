plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    // SPI only: this backend depends on :engine for AudioBackend/Sound and on
    // nothing else — no render module (:engine-skiko/:engine-lwjgl) and no
    // third-party audio lib. Decode + playback ride entirely on the JDK's
    // `javax.sound.sampled`, so the module stays host-agnostic (invariant #4).
    implementation(projects.engine)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
