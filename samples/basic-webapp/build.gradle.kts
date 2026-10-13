plugins {
    id("java")
    id("war")
    alias(libs.plugins.bmuschko.tomcat)
}

description = "Sample Web Application"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jodconverter.local.lo)

    implementation(libs.commons.fileupload)
    implementation(libs.slf4j.api)

    compileOnly(libs.servlet.api)

    runtimeOnly(libs.slf4j.log4j12)

    tomcat(libs.tomcat.embed.core)
    tomcat(libs.tomcat.embed.jasper)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

tomcat {
    // Set explicitly: the default of the plugin reads a property of the war task that Gradle 8
    // removed, and tomcatRun fails without it.
    contextPath = "jodconverter-sample-webapp"
    httpProtocol = "org.apache.coyote.http11.Http11Nio2Protocol"
    ajpProtocol = "org.apache.coyote.ajp.AjpNio2Protocol"
}