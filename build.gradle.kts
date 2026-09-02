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
    dependsOn(":frontend:dev")
}
