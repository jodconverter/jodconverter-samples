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

If you experience connection issue on Windows 10, you may have to set a system property pointing to a templateProfileDir where OpenGL is disabled by default. Read [this](https://wiki.documentfoundation.org/OpenGL#:~:text=LibreOffice%205.3%20and%20newer%3A,Click%20%22Apply%20Changes%20and%20Restart%22) to know how to disable OpenGL. Then, run:

```Shell
gradlew :samples:basic-webapp:tomcatRun -Dorg.jodconverter.local.manager.templateProfileDir=<path to your directory>
```

Once started, use your favorite browser and visit this page:

```
http://localhost:8080/jodconverter-sample-webapp/
```

Happy conversions!!