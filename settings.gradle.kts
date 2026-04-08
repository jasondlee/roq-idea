plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.4.0"
}

rootProject.name = "roqidea"

// Build Scan configuration for performance monitoring
// Publish with: ./gradlew build --scan
develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        publishing.onlyIf { false } // Only publish when explicitly requested with --scan
    }
}
