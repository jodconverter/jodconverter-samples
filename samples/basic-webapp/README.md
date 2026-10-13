## JODConverter - Sample - Webapp

This is a sample web application that uses the local module of the Java OpenDocument Converter (JODConverter) project.

### Running the Project using gradle

```Shell
gradlew :samples:basic-webapp:run
```

The task builds the war file and runs it in an embedded Tomcat, on port 8080; add `-Pport=8081` for another port, and stop it with Ctrl+C.

The application is a plain Jakarta Servlet 6 application: the war file (`build/libs`) can be deployed as is to Tomcat 10.1 or later, or to any other Jakarta EE 10 servlet container. The embedded Tomcat only belongs to the `run` task and to the tests (`src/launcher`); it is not in the war file.

The size of an upload is limited by the `multipart-config` of the servlet in `src/main/webapp/WEB-INF/web.xml`, and the logging is configured in `src/main/resources/simplelogger.properties`.

The office home, the office port and a template profile directory (the settings the office processes start with) are context parameters of `src/main/webapp/WEB-INF/web.xml`: uncomment and edit the `office.home`, `office.port` and `office.profile` parameters there. A profile where OpenGL is disabled may help if the office does not start on some Windows machines; read [this](https://wiki.documentfoundation.org/OpenGL) to know how to disable it.

Once started, use your favorite browser and visit this page:

```
http://localhost:8080/jodconverter-sample-webapp/
```

Happy conversions!!