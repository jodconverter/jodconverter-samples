## JODConverter - Sample - Spring Boot

This is a sample application that uses the spring boot integration module of the Java OpenDocument Converter (JODConverter) project.

### Running the Project using gradle

First, build the project:

```Shell
gradlew :samples:spring-boot-webapp:build
```

Then, run:

```Shell
gradlew :samples:spring-boot-webapp:bootRun
```

Once started, use your favorite browser and visit this page:

```
http://localhost:8080/
```

### What the sample shows

The page converts a document with the converter that the JODConverter starter creates. Its "Options" show features of JODConverter 5:

- **Password of the document**: `password(...)` on the conversion, for a protected document. Without it, the page says that the document is protected.
- **Landscape pages** and **Remove the page header and footer**: the `PageOrientationFilter` and the `HeaderFooterRemoverFilter`. Try the second with a CSV file: without it, each page has the name of the sheet at the top and "Page 1" at the bottom.
- **PDF options**, when the output is a PDF document: a preset of `PdfOptions` (archive for PDF/A-2b, accessible for PDF/UA, compact) and a page range.

`application.yml` sets a default PDF option for every conversion with `jodconverter.pdf.tagged`, and the options of the HTML formats with `jodconverter.document-formats.options`.

Two things in `ConverterController` are worth reading before copying it. The upload is given to the converter with the format its name tells, because the office takes a CSV stream for a text otherwise. And filters belong to a converter, not to a conversion: a conversion that needs some builds a converter of its own on the office manager of the starter.

Happy conversions!!