plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.sonarqube") version "7.6.0.18749"
}

sonar {
    properties {
        property("sonar.projectKey", "swimgym")
        property("sonar.projectName", "SwimGym")
        property("sonar.host.url", "https://sonar.joeri.nu")
        property("sonar.sourceEncoding", "UTF-8")
        providers.gradleProperty("sonar.token")
            .orElse(providers.environmentVariable("SONAR_TOKEN"))
            .orNull
            ?.let { property("sonar.token", it) }
    }
}