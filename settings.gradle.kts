rootProject.name = "nengine"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":engine")
include(":engine-bundle")
include(":engine-bundle-python")
include(":engine-bundle-lua")
include(":engine-skiko")
include(":engine-lwjgl")
include(":engine-audio-javasound")
include(":games:pong")
include(":games:tictactoe")
include(":games:demos")
include(":games:hello-world")
include(":games:snake")
