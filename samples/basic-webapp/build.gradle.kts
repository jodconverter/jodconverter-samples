plugins {
    id("java")
    id("war")

    // https://plugins.gradle.org/plugin/com.bmuschko.tomcat
    id("com.bmuschko.tomcat") version "2.7.0"
}

description = "Sample Web Application"

val jodcVersion by extra { "4.4.6-SNAPSHOT" }
// https://search.maven.org/artifact/org.slf4j/slf4j-api
val slf4jVersion by extra { "2.0.6" }
// https://search.maven.org/artifact/commons-fileupload/commons-fileupload
val commonsFileUploadVersion by extra { "1.4" }
// https://search.maven.org/artifact/javax.servlet/javax.servlet-api
val servletApiVersion by extra { "4.0.1" }
// https://search.maven.org/artifact/org.apache.tomcat.embed/tomcat-embed-core
// We can't use 9.0.31+ until this is resolved:
// https://github.com/bmuschko/gradle-tomcat-plugin/pull/209
val tomcatVersion by extra { "9.0.30" }

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation("org.jodconverter:jodconverter-local-lo:$jodcVersion")

    implementation("commons-fileupload:commons-fileupload:$commonsFileUploadVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    compileOnly("javax.servlet:javax.servlet-api:$servletApiVersion")

    runtimeOnly("org.slf4j:slf4j-log4j12:$slf4jVersion")

    tomcat("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
    tomcat("org.apache.tomcat.embed:tomcat-embed-jasper:$tomcatVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tomcat {
    httpProtocol = "org.apache.coyote.http11.Http11Nio2Protocol"
    ajpProtocol = "org.apache.coyote.ajp.AjpNio2Protocol"
}