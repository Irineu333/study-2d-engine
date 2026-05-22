plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
