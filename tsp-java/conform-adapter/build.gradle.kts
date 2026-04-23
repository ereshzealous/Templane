plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("dev.tsp.conform.ConformAdapter")
}

dependencies {
    implementation(project(":tsp-core"))
    implementation(project(":tsp-adapter-html"))
    implementation(project(":tsp-adapter-yaml"))
}

tasks.shadowJar {
    archiveBaseName.set("conform-adapter")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
}

tasks.named("publish") { enabled = false }
tasks.named("publishToMavenLocal") { enabled = false }
