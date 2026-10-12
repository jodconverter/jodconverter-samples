## JODConverter - Sample - Webapp

This is a sample web application that uses the local module of the Java OpenDocument Converter (JODConverter) project.

### Running the Project using gradle

First, build the project:

```Shell
gradlew :samples:basic-webapp:build
```

Then, run:

```Shell
gradlew :samples:basic-webapp:tomcatRun
```

The office home, the office port and a template profile directory (the settings the office processes start with) are context parameters of `src/main/webapp/WEB-INF/web.xml`: uncomment and edit the `office.home`, `office.port` and `office.profile` parameters there. A profile where OpenGL is disabled may help if the office does not start on some Windows machines; read [this](https://wiki.documentfoundation.org/OpenGL) to know how to disable it.

Once started, use your favorite browser and visit this page:

```
http://localhost:8080/jodconverter-sample-webapp/
```

Happy conversions!!