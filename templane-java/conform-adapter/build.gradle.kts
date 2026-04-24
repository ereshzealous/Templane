plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("dev.templane.conform.ConformAdapter")
}

dependencies {
    implementation(project(":templane-core"))
    implementation(project(":templane-adapter-html"))
    implementation(project(":templane-adapter-yaml"))
}

tasks.shadowJar {
    archiveBaseName.set("conform-adapter")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
}

// We ship only the fat JAR (shadowJar). Disable the application-plugin
// and shadow-plugin distribution tasks — they read shadowJar's output
// without declaring a dependency, which fails Gradle 8+ validation.
tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }
tasks.named("startScripts") { enabled = false }
tasks.named("shadowDistTar") { enabled = false }
tasks.named("shadowDistZip") { enabled = false }
tasks.named("startShadowScripts") { enabled = false }

tasks.named("publish") { enabled = false }
tasks.named("publishToMavenLocal") { enabled = false }
