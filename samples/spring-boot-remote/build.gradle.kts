plugins {
    id("java")
    alias(libs.plugins.spring.boot)
}

description = "Sample Spring Boot Application converting with a remote server"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // The remote module, and no office library: the application does not start an office, it sends
    // the documents to a Collabora Online (or LibreOffice Online) server.
    implementation(libs.jodconverter.remote)
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
