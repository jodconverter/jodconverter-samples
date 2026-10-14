## JODConverter - Sample - Remote conversion

This is a sample application that converts documents without an office of its own. It uses the remote module of the Java OpenDocument Converter (JODConverter) project, through the spring boot integration module, to send the documents to a [Collabora Online](https://www.collaboraonline.com/) server and to get them back converted.

It is the shape of a deployment where the conversions are a service of their own, in a container or in a cluster: the application stays small (no LibreOffice in its image), and the conversion server is scaled, updated and restarted apart from it.

### Running the Project using gradle

LibreOffice is not needed. Docker is, for the conversion server.

First, start the conversion server (Collabora Online Development Edition) with the `compose.yaml` file of this directory:

```Shell
docker compose -f samples/spring-boot-remote/compose.yaml up -d
```

Then, run the application:

```Shell
gradlew :samples:spring-boot-remote:bootRun
```

Once started, convert a document:

```Shell
curl -f -o converted.pdf -F "file=@document.docx" -F format=pdf http://localhost:8080/convert
```

`format` is the extension of the target format (`pdf`, `docx`, `odt`, `xlsx`...). The application answers 400 for a request it cannot use, and 502 when the conversion server refuses the document, fails, or cannot be reached.

When you are done:

```Shell
docker compose -f samples/spring-boot-remote/compose.yaml down
```

### How it works

- `jodconverter.remote.enabled` and `jodconverter.remote.url` in `src/main/resources/application.yml` are all the configuration: the starter creates a `DocumentConverter` that sends each document to the `convert-to` service of the server. The controller uses it exactly as it would use a converter that drives a local office.
- The address of the server comes from the `CONVERSION_SERVER_URL` environment variable when it is set (`http://localhost:9980/cool/` otherwise), so the same build runs beside any server.
- `jodconverter.remote.pool-size` is how many conversions are sent to the server at the same time; the others wait in the queue of JODConverter.

### What a remote conversion cannot do

The `convert-to` service takes a document and a target format, nothing more. The filters of the local module, the page images and the merge of documents need a local office. The load and store properties of a conversion, the PDF options among them, are sent to the server as request parameters, but Collabora Online does not read them; the [REST sample](../spring-boot-rest) of this repository is a conversion server that does.

### Over a network

`compose.yaml` publishes the server on this machine only, over plain HTTP. A conversion server converts for whoever can reach it: on a network, keep it private to the applications that use it, keep its TLS (or put it behind a proxy that has it), and give the trust store of the application with the `jodconverter.remote.ssl.*` properties. See [LibreOffice Online](https://jodconverter.github.io/jodconverter/latest/getting-started/libreoffice-online/) in the documentation.

### The tests

The tests need neither an office nor a container: a small HTTP server stands in for the conversion server, and the tests check what the application sends to it and what it answers.
