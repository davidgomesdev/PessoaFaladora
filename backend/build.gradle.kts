plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    kotlin("plugin.serialization")
    kotlin("plugin.jpa")
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val langchainMarkdownVersion = "1.8.0-beta15"
val kotlinxSerializationJson = "1.9.0"
val mutinyVersion = "2.0.0"
val otelExtension = "1.59.0"
val qdrantLangchainVersion = "1.12.2-beta22"
val mockito = "5.2.1"
val nimbusJoseJwtVersion = "10.7"
val arrowVersion = "2.2.2.1"

// Fixes Quarkus' BOM imposing a broken Qdrant version
configurations.all {
    resolutionStrategy {
        force("dev.langchain4j:langchain4j-qdrant:$qdrantLangchainVersion")
    }
}

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation(enforcedPlatform("dev.langchain4j:langchain4j-bom:1.12.2"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJson")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-qute")
    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-ollama")
    implementation("dev.langchain4j:langchain4j-anthropic")
    implementation("dev.langchain4j:langchain4j-voyage-ai")
    implementation("dev.langchain4j:langchain4j-qdrant:$qdrantLangchainVersion")
    implementation("dev.langchain4j:langchain4j-document-parser-markdown:$langchainMarkdownVersion")
    implementation("io.smallrye.reactive:mutiny-kotlin:$mutinyVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-opentelemetry")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:$otelExtension")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    implementation("io.quarkus:quarkus-hibernate-orm-panache-kotlin")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("com.github.f4b6a3:uuid-creator:6.0.0")
    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation(project(":shared"))
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockito")
    testImplementation("io.quarkus:quarkus-jdbc-h2")
}

group = "me.davidgomesdev.pessoafaladora"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
    }
}

val uiBuildDir = project(":composeApp").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")

val generateUiBundle by tasks.registering(Copy::class) {
    group = "build"
    description = "Builds the Compose web bundle and stages it as backend static resources"
    dependsOn(":composeApp:jsBrowserProductionWebpack")
    from(uiBuildDir) {
        exclude("index.html", "composeResources/**")
        into("META-INF/resources/static")
    }
    from(uiBuildDir.map { it.dir("composeResources") }) {
        into("META-INF/resources/composeResources")
    }
    into(layout.buildDirectory.dir("generated-ui"))
}

sourceSets.main {
    resources.srcDir(generateUiBundle)
}
