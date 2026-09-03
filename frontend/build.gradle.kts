import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.npm.task.NpxTask

plugins {
    base
    alias(libs.plugins.node)
}

node {
    version.set("22.23.2")
    download.set(true)
    nodeProjectDir.set(file("$projectDir"))
}

tasks.register<NpmTask>("lint") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "lint"))
    inputs.dir("src")
    inputs.file("eslint.config.js")
}

tasks.register<NpmTask>("test") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "test:ci"))
    inputs.dir("src")
    outputs.dir("coverage")
}

tasks.register<NpmTask>("buildFe") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "build"))
    inputs.dir("src")
    outputs.dir("dist")
}

tasks.check {
    dependsOn("lint", "test")
}

tasks.assemble {
    dependsOn("buildFe")
}

// Single-terminal local dev run: Postgres (docker compose) + backend (Gradle) + this
// frontend, multiplexed with colored prefixes. Registered here (not root) because the
// node plugin, and `concurrently` (installed as a devDependency), live in this module.
tasks.register<NpxTask>("dev") {
    dependsOn(tasks.npmInstall)
    command.set("concurrently")
    // Gradle pipes the child process's output rather than attaching a real TTY, so chalk/
    // concurrently's own TTY auto-detection disables color; force it back on.
    environment.put("FORCE_COLOR", "1")
    args.set(
        listOf(
            "-k",
            "--names",
            "docker,backend,frontend",
            "-c",
            "blue,green,magenta",
            "docker compose -f ../local-environment/docker-compose.yml up",
            "cd .. && SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun",
            "npm start",
        )
    )
}
