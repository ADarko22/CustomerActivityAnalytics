plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
}

group = "io.github.adarko22"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.springdoc.openapi.ui)

    // Persistence & DB
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // Security
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)

    // AI
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.ai.anthropic)

    // Management & Observability
    implementation(libs.spring.boot.starter.actuator)

    // Test
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.wiremock.standalone)
    testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.ai.bom.get().toString())
        mavenBom(libs.testcontainers.bom.get().toString())
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

spotless {
    java {
        googleJavaFormat("1.27.0")
        target("src/**/*.java")
    }
}

tasks.check {
    dependsOn(tasks.spotlessCheck, tasks.jacocoTestReport)
}