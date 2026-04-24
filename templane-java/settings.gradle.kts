plugins {
    id("com.gradleup.nmcp.settings").version("1.4.4")
}

val centralUsername = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").orNull
    ?: providers.gradleProperty("mavenCentralUsername").orNull
val centralPassword = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").orNull
    ?: providers.gradleProperty("mavenCentralPassword").orNull
val automaticRelease = (
    providers.environmentVariable("MAVEN_CENTRAL_AUTOMATIC_RELEASE").orNull
        ?: providers.gradleProperty("mavenCentralAutomaticRelease").orNull
)?.toBoolean() ?: false

nmcpSettings {
    if (!centralUsername.isNullOrBlank() && !centralPassword.isNullOrBlank()) {
        centralPortal {
            username = centralUsername
            password = centralPassword
            publishingType = if (automaticRelease) "AUTOMATIC" else "USER_MANAGED"
            publicationName = "templane-java"
        }
    }
}

rootProject.name = "templane-java"

include(
    "templane-core",
    "templane-adapter-html",
    "templane-adapter-yaml",
    "freemarker-templane",
    "conform-adapter",
    "examples",
)
