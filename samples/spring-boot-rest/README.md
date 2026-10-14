## JODConverter - Sample - Rest API

This is a sample application of a rest api that uses the spring boot integration module of the Java OpenDocument Converter (JODConverter) project to offer document conversion capabilities. The goal was to emulate a LibreOffice Online server that
would support the customization of custom (known) load/store properties.

Hint: The exposed REST-API interface imitates the LibreOffice-Online REST interface (/lool), you could think of this example as an LibreOffice-Online server.
This being a LibreOffice-Online server, you can talk to the REST api using jodconverter-remote (LO online client). (`-c parameter`)

### Update

See [here](https://github.com/EugenMayer/converter) for an improved version, meaning production ready, of this project!

### Running the Project using gradle

First, build the project:

```Shell
gradlew :samples:spring-boot-rest:build
```

Then, run:

```Shell
gradlew :samples:spring-boot-rest:bootRun
```

Once started, use your favorite browser and visit this page:

```
http://localhost:8080/swagger-ui.html
```

You can also ge the openapi specs from 

```
http://localhost:8080/rest/api
```

### Parameters of a conversion

The request parameters whose name starts with `l` or `s` become load and store properties of the conversion (`sFilterName=writer_pdf_Export` for example), and those that start with `lfd` or `sfd` go to the `FilterData` of the load or of the store. This is what lets a `jodconverter-remote` client send its options, and it means that the caller decides how the office reads and writes the document.

Two load properties cannot be set by a request, which answers 400 when one is given: `UpdateDocMode` and `MacroExecutionMode`. They decide whether the office follows the links of the document and runs its macros, and they stay what JODConverter sets by default. Do not expose this API to callers you do not trust without restricting the other properties too, and read the [security page](https://jodconverter.github.io/jodconverter/latest/getting-started/security/) of JODConverter.

When a conversion fails, the answer is a short message; the details are in the log of the application.

### Password and description of the office

A `password` parameter opens a protected document; without it, or with a wrong one, the answer says so:

```
POST http://localhost:8080/lool/convert-to/pdf?password=secret
```

The name and the version of the office that converts the documents, and its import and export filters, are at:

```
http://localhost:8080/info
```

This is the `OfficeInfoTask` of JODConverter. The application asks the office once and keeps the answer.

### Previews and slide images

Two endpoints make images of documents, for thumbnails or a preview before a download:

```
POST http://localhost:8080/preview          data=<document>  [width=320]
POST http://localhost:8080/pages            data=<presentation or drawing>  [width=320] [format=png] [pages=1-3,7]
```

`/preview` answers a PNG image of the first page of any document (text, spreadsheet, presentation, drawing), of the given width; the height keeps the proportions of the page. The office draws the page at its own size, and the application scales the image: the export filters of the office need the width and the height together, and the height is only known once the page is drawn.

`/pages` answers a ZIP file with one image per slide of a presentation, or per page of a drawing (`page-1.png`, `page-2.png`...), in PNG, JPEG (`jpg`), SVG or WebP. It uses the `exportPages` job of the `LocalConverter`, which only exports presentations and drawings; a text document is refused. See [Slides to Images](https://jodconverter.github.io/jodconverter/latest/getting-started/page-images/) in the documentation.

```
curl -f -o thumbnail.png -F "data=@report.docx" -F width=200 http://localhost:8080/preview
curl -f -o slides.zip -F "data=@deck.pptx" -F format=jpg http://localhost:8080/pages
```

### State of the office processes

The sample includes Spring Boot Actuator, with which the JODConverter starter reports the state of the office processes:

```
http://localhost:8080/actuator/health
http://localhost:8080/actuator/metrics/jodconverter.workers
```

The health has a `jodconverter` component with the state of each office process and the size of the queue. The meters are `jodconverter.queue.size`, `jodconverter.tasks.retries`, `jodconverter.workers` (with a `state` tag), `jodconverter.workers.tasks`, `jodconverter.workers.restarts` and `jodconverter.workers.start.failures`.

Happy conversions!!
