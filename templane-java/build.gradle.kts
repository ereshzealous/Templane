import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.plugins.signing.SigningExtension

plugins {
    java
}

val publishableModules = setOf(
    "templane-core",
    "templane-adapter-html",
    "templane-adapter-yaml",
    "freemarker-templane",
)

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    project.group = "io.github.ereshzealous"
    project.version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Javadoc>().configureEach {
        if (JavaVersion.current().isJava9Compatible) {
            (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
        }
    }

    if (project.name in publishableModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = project.name

                    pom {
                        name.set("templane ${project.name}")
                        description.set(
                            when (project.name) {
                                "templane-core" -> "Core Java implementation of the Templane protocol."
                                "templane-adapter-html" -> "HTML adapter for the Java implementation of Templane."
                                "templane-adapter-yaml" -> "YAML adapter for the Java implementation of Templane."
                                "freemarker-templane" -> "FreeMarker binding for the Java implementation of Templane."
                                else -> "Java module for Templane."
                            }
                        )
                        url.set("https://github.com/ereshzealous/Templane")
                        inceptionYear.set("2026")

                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }

                        developers {
                            developer {
                                id.set("ereshzealous")
                                name.set("Eresh Gorantla")
                                url.set("https://github.com/ereshzealous")
                            }
                        }

                        scm {
                            url.set("https://github.com/ereshzealous/Templane")
                            connection.set("scm:git:git://github.com/ereshzealous/Templane.git")
                            developerConnection.set("scm:git:ssh://git@github.com/ereshzealous/Templane.git")
                        }
                    }
                }
            }
        }

        val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
            ?: System.getenv("SIGNING_KEY")
        val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
            ?: System.getenv("SIGNING_PASSWORD")

        extensions.configure<SigningExtension> {
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType(PublishingExtension::class.java).publications["mavenJava"])
            }
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.1"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.24.2")
    }
}
