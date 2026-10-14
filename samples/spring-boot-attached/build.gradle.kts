plugins {
    id("java")
    alias(libs.plugins.spring.boot)
}

description = "Sample Spring Boot Application converting with an office it does not start"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // The local module and the office libraries of LibreOffice: they talk to the office over its
    // UNO connection. The office itself runs apart, in a container (office/Dockerfile).
    implementation(libs.jodconverter.local.lo)
    implementation(libs.jodconverter.spring.boot.starter)

    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter.api)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
