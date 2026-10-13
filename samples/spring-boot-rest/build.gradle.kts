plugins {
    id("java")
    id("war")
    alias(libs.plugins.spring.boot)
}

description = "Sample Spring Boot Rest Api"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(libs.jodconverter.local.lo)
    implementation(libs.jodconverter.spring.boot.starter)

    implementation(libs.spring.boot.starter.web)
    // The health indicator and the meters of JODConverter appear with the actuator.
    implementation(libs.spring.boot.starter.actuator)

    implementation(libs.commons.io)
    implementation(libs.jaxb.runtime)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.swagger.annotations)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter.api)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

// The office processes can be started with settings of your own, from a template profile
// directory (see the templateProfileDir option of JODConverter); for example a profile where
// OpenGL is disabled, if the office does not start on some Windows machines:
// https://wiki.documentfoundation.org/OpenGL
// Give the directory to the test and bootRun tasks with a project property:
//   gradlew :samples:spring-boot-rest:bootRun -PtemplateProfileDir=<path to your directory>
// It is passed as the jodconverter.local.template-profile-dir property of the starter.

tasks.getByName<Test>("test") {
    useJUnitPlatform()

    project.findProperty("templateProfileDir")?.let {
        systemProperty("jodconverter.local.template-profile-dir", it)
    }
}

tasks.bootRun {
    project.findProperty("templateProfileDir")?.let {
        systemProperty("jodconverter.local.template-profile-dir", it)
    }
}
