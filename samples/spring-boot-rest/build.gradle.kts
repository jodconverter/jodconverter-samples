plugins {
    id("java")
    id("war")
    id("org.springframework.boot") version Versions.springBoot
}

description = "Sample Spring Boot Rest Api"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${Versions.springBoot}"))

    implementation("org.jodconverter:jodconverter-local-lo:${Versions.Dependencies.jodConverter}")
    implementation("org.jodconverter:jodconverter-spring-boot-starter:${Versions.Dependencies.jodConverter}")

    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("commons-io:commons-io:${Versions.Dependencies.commonsIo}")
    implementation("org.glassfish.jaxb:jaxb-runtime")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${Versions.Dependencies.openApi}")
    implementation("io.swagger.core.v3:swagger-annotations:${Versions.Dependencies.swagger}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

java {
    sourceCompatibility = Versions.jvm
    targetCompatibility = Versions.jvm
}

// If you experience connection issue on Windows 10, you may have to set a project property
// pointing to a templateProfileDir where OpenGL is disabled by default. This must be done
// for the Test and BootRun tasks.
// Read here to know how to disable OpenGL:
// https://wiki.documentfoundation.org/OpenGL#:~:text=LibreOffice%205.3%20and%20newer%3A,Click%20%22Apply%20Changes%20and%20Restart%22

tasks.getByName<Test>("test") {
    useJUnitPlatform()

    project.findProperty("org.jodconverter.local.manager.templateProfileDir")?.let {
        systemProperty("org.jodconverter.local.manager.templateProfileDir", it)
    }
}

tasks.bootRun {
    project.findProperty("org.jodconverter.local.manager.templateProfileDir")?.let {
        systemProperty("org.jodconverter.local.manager.templateProfileDir", it)
    }
}