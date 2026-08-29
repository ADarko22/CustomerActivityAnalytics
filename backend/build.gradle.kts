plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
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

    // Management & Observability
    implementation(libs.spring.boot.starter.actuator)

    // Test
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.security.oauth2.resource.server.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.ai.bom.get().toString())
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}