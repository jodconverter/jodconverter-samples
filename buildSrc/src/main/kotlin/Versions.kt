import org.gradle.api.JavaVersion

object Versions {

    val jvm = JavaVersion.VERSION_17

    //  Latest Version:
    //  Visit https://central.sonatype.dev and search for: g:org.springframework.boot a:spring-boot
    const val springBoot = "3.0.2"

    object Plugins {

        // Latest Version: https://plugins.gradle.org/plugin/com.bmuschko.tomcat
        const val bmuschkoTomcat = "2.7.0"
    }

    object Dependencies {

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:org.jodconverter a:jodconverter-core
        const val jodConverter = "4.4.6"

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:commons-fileupload a:commons-fileupload
        const val commonsFileUpload = "1.4"

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:commons-io a:commons-io
        const val commonsIo = "2.11.0"

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:org.springdoc a:springdoc-openapi-starter-webmvc-ui
        const val openApi = "2.0.2"

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:javax.servlet a:javax.servlet-api
        const val servletApi = "4.0.1"

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:org.slf4j a:slf4j-api
        const val slf4j = "2.0.6"

        //  Latest Version:
        //  Visit https://central.sonatype.dev and search for: g:io.swagger.core.v3 a:swagger-project
        const val swagger = "2.2.8"

        // https://search.maven.org/artifact/org.apache.tomcat.embed/tomcat-embed-core
        // We can't use 9.0.31+ until this is resolved:
        // https://github.com/bmuschko/gradle-tomcat-plugin/pull/209
        const val tomcat = "9.0.30"
    }
}