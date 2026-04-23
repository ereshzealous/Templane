plugins {
    java
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    project.group = "dev.templane"
    project.version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.1"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.24.2")
    }
}
