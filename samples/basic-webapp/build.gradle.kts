plugins {
    id("java")
    id("war")
    id("com.bmuschko.tomcat") version Versions.Plugins.bmuschkoTomcat
}

description = "Sample Web Application"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jodconverter:jodconverter-local-lo:${Versions.Dependencies.jodConverter}")

    implementation("commons-fileupload:commons-fileupload:${Versions.Dependencies.commonsFileUpload}")
    implementation("org.slf4j:slf4j-api:${Versions.Dependencies.slf4j}")

    compileOnly("javax.servlet:javax.servlet-api:${Versions.Dependencies.servletApi}")

    runtimeOnly("org.slf4j:slf4j-log4j12:${Versions.Dependencies.slf4j}")

    tomcat("org.apache.tomcat.embed:tomcat-embed-core:${Versions.Dependencies.tomcat}")
    tomcat("org.apache.tomcat.embed:tomcat-embed-jasper:${Versions.Dependencies.tomcat}")
}

java {
    sourceCompatibility = Versions.jvm
    targetCompatibility = Versions.jvm
}

tomcat {
    httpProtocol = "org.apache.coyote.http11.Http11Nio2Protocol"
    ajpProtocol = "org.apache.coyote.ajp.AjpNio2Protocol"
}