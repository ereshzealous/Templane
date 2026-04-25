plugins {
    application
}

application {
    // Default mainClass; the :runExample task below overrides via -P.
    mainClass.set("dev.templane.examples.Hello")
}

dependencies {
    implementation(project(":templane-core"))
    implementation(project(":templane-adapter-html"))
    implementation(project(":templane-adapter-yaml"))
    implementation(project(":freemarker-templane"))
}

// Run a specific example: ./gradlew :examples:runExample -Pmain=dev.templane.examples.Hello
tasks.register<JavaExec>("runExample") {
    group = "examples"
    description = "Run a named example (pass -Pmain=<fqcn>)"
    mainClass.set(providers.gradleProperty("main").orElse("dev.templane.examples.Hello"))
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    standardInput = System.`in`
}

tasks.matching { it.name == "publish" || it.name == "publishToMavenLocal" }
    .configureEach { enabled = false }
tasks.named("distTar") { enabled = false }
tasks.named("distZip") { enabled = false }
tasks.named("startScripts") { enabled = false }
