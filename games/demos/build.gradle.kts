plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineSkiko)
}

application {
    mainClass.set("com.neoutils.engine.games.demos.MainKt")
}
