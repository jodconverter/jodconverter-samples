plugins {
    id("java")
    id("war")
    id("org.springframework.boot") version Versions.springBoot
}

description = "Sample Spring Boot Web Application"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${Versions.springBoot}"))

    implementation("org.jodconverter:jodconverter-local-lo:${Versions.Dependencies.jodConverter}")
    implementation("org.jodconverter:jodconverter-spring-boot-starter:${Versions.Dependencies.jodConverter}")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("commons-io:commons-io:${Versions.Dependencies.commonsIo}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
}

java {
    sourceCompatibility = Versions.jvm
    targetCompatibility = Versions.jvm
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
