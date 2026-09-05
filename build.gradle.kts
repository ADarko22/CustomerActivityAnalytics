plugins {
    base
    alias(libs.plugins.sonarqube)
}

group = "io.github.adarko22"
version = "0.0.1-SNAPSHOT"

tasks.check {
    dependsOn(":backend:check", ":frontend:check")
}

tasks.build {
    dependsOn(":backend:build", ":frontend:assemble")
}

// Single-terminal local dev entry point: `./gradlew dev`. The actual multiplexed
// run task lives in `frontend/build.gradle.kts` (Postgres + backend + frontend),
// where the node plugin and `concurrently` are already available.
tasks.register("dev") {
    group = "application"
    description = "Delegates to :frontend:dev to run the full local stack from one terminal."
    dependsOn(":frontend:dev")
}

sonar {
    properties {
        property("sonar.projectKey", "ADarko22_CustomerActivityAnalytics")
        property("sonar.organization", "adarko22-dev")
        // Flyway SQL has no dedicated PostgreSQL analyzer here — see docs/DECISIONS.md D22.
        property("sonar.exclusions", "backend/src/main/resources/db/**")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "backend/build/reports/jacoco/test/jacocoTestReport.xml",
        )
        property("sonar.javascript.lcov.reportPaths", "frontend/coverage/frontend/lcov.info")
    }
}