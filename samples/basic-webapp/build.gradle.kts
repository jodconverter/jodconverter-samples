plugins {
    id("java")
    id("war")
}

description = "Sample Web Application"

repositories {
    mavenCentral()
}

// The launcher runs the web application in an embedded Tomcat. It has a source set of its own:
// neither its code nor Tomcat goes into the war file, which is a plain Jakarta Servlet
// application for any servlet container.
val launcher: SourceSet = sourceSets.create("launcher")

dependencies {
    implementation(libs.jodconverter.local.lo)
    implementation(libs.slf4j.api)

    // Given by the servlet container.
    compileOnly(libs.jakarta.servlet.api)

    runtimeOnly(libs.slf4j.simple)

    // Tomcat, with its JSP engine: the application has no JSP page, but a servlet container
    // declares the JSP servlet for every application, and logs an error when it is missing.
    "launcherImplementation"(libs.tomcat.embed.core)
    "launcherRuntimeOnly"(libs.tomcat.embed.jasper)

    // The test starts the launcher with the war file, and calls the application over HTTP.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(launcher.output)
    testImplementation(libs.tomcat.embed.core)
    testRuntimeOnly(libs.tomcat.embed.jasper)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

// The sources are UTF-8, whatever the default encoding of the machine that compiles them.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val warFile = tasks.war.flatMap { it.archiveFile }

// gradlew :samples:basic-webapp:run [-Pport=8080]
val port = (findProperty("port") ?: "8080").toString()

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the web application in an embedded Tomcat."

    dependsOn(tasks.war)
    classpath = launcher.runtimeClasspath
    mainClass = "org.jodconverter.sample.webapp.launcher.Launcher"
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(warFile.get().asFile.absolutePath, port)
        }
    )
}

tasks.test {
    useJUnitPlatform()

    dependsOn(tasks.war)
    inputs.file(warFile)
    systemProperty("sample.war", warFile.get().asFile.absolutePath)
}
