plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineSkiko)
}

application {
    mainClass.set("com.neoutils.engine.games.demos.MainKt")
}
