import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm()

    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.regotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.napier)
            implementation(libs.arrow.core)
            implementation(project(":shared"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(devNpm("copy-webpack-plugin", "12.0.2"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.okhttp)
            runtimeOnly(libs.logging)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ofingidor.composeapp.generated.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "me.davidgomesdev.ofingidor.ui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "me.davidgomesdev.ofingidor.ui"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<AbstractCopyTask>().configureEach {
    if (name.startsWith("js") || name.startsWith("composeApp")) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
