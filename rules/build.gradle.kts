plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Annotations only, so :protocol can send cards and rule sets as-is.
    api(libs.kotlinx.serialization.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    systemProperty("fuzz.games", providers.gradleProperty("fuzzGames").getOrElse("2000"))
}
