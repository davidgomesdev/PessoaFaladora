plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
}

val kotlinxSerializationJson = "1.9.0"

kotlin {
    jvm()
    js { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJson")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.register("test") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Alias for shared jvmTest"
    dependsOn("jvmTest")
}
