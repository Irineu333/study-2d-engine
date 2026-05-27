plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineSkiko)
    implementation(projects.engineBundle)
    implementation(projects.engineBundlePython)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

application {
    mainClass.set("com.neoutils.engine.games.snake.MainKt")
}
