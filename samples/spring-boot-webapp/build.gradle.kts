plugins {
    id("java")
    id("war")
    alias(libs.plugins.spring.boot)
}

description = "Sample Spring Boot Web Application"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(libs.jodconverter.local.lo)
    implementation(libs.jodconverter.spring.boot.starter)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.thymeleaf)

    // The development tools restart the application when a class changes. They are for bootRun
    // only: declared as developmentOnly, they are not packaged in the war that is deployed.
    developmentOnly(platform(libs.spring.boot.dependencies))
    developmentOnly(libs.spring.boot.devtools)

    implementation(libs.commons.io)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter.api)

    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

// The plain war, the one for a servlet container, is made from the runtime classpath, which has
// the development tools: they are left out of it, as the Spring Boot plugin leaves them out of
// the executable war.
tasks.war {
    classpath?.let { files ->
        setClasspath(files.filter { !it.name.startsWith("spring-boot-devtools") })
    }
}

// The office processes can be started with settings of your own, from a template profile
// directory (see the templateProfileDir option of JODConverter); for example a profile where
// OpenGL is disabled, if the office does not start on some Windows machines:
// https://wiki.documentfoundation.org/OpenGL
// Give the directory to the test and bootRun tasks with a project property:
//   gradlew :samples:spring-boot-webapp:bootRun -PtemplateProfileDir=<path to your directory>
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
