plugins {
    id("java")
    id("war")
    id("org.springframework.boot") version "3.0.2"
}

description = "Sample Spring Boot Web Application"

val jodcVersion by extra { "4.4.6-SNAPSHOT" }
// https://search.maven.org/artifact/org.springframework.boot/spring-boot
val springBootVersion by extra { "3.0.2" }
// https://search.maven.org/artifact/commons-io/commons-io
val commonsIoVersion by extra { "2.11.0" }

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    implementation("org.jodconverter:jodconverter-local-lo:$jodcVersion")
    implementation("org.jodconverter:jodconverter-spring-boot-starter:$jodcVersion")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("commons-io:commons-io:$commonsIoVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()

    // If you experience connection issue on Windows 10, you may have to set a project property
    // pointing to a templateProfileDir where OpenGL is disabled by default. Read here to know
    // how to disable OpenGL:
    // https://wiki.documentfoundation.org/OpenGL#:~:text=LibreOffice%205.3%20and%20newer%3A,Click%20%22Apply%20Changes%20and%20Restart%22
    project.findProperty("org.jodconverter.local.manager.templateProfileDir")?.let {
        systemProperty("org.jodconverter.local.manager.templateProfileDir", it)
    }
}

tasks.bootRun {
    // If you experience connection issue on Windows 10, you may have to set a project property
    // pointing to a templateProfileDir where OpenGL is disabled by default. Read here to know
    // how to disable OpenGL:
    // https://wiki.documentfoundation.org/OpenGL#:~:text=LibreOffice%205.3%20and%20newer%3A,Click%20%22Apply%20Changes%20and%20Restart%22
    project.findProperty("org.jodconverter.local.manager.templateProfileDir")?.let {
        systemProperty("org.jodconverter.local.manager.templateProfileDir", it)
    }
}