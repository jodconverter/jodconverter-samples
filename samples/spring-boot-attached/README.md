## JODConverter - Sample - Attached office

This is a sample application that converts with an office it does not start. The office, LibreOffice alone, runs in a container and listens for UNO connections; the application connects to it with the attached manager of the Java OpenDocument Converter (JODConverter) project, through the spring boot integration module, and sends it the documents.

It is the shape of a deployment where the office is a process of its own: in a container next to the application, or on another machine. The application image has no LibreOffice in it, and the office is updated and restarted apart from it. The attached manager was named `ExternalOfficeManager` before JODConverter 5.0 (the `jodconverter.external.*` properties); the old names still work, deprecated.

### Running the Project using gradle

LibreOffice is not needed on this machine, Docker is.

First, build the office image and start it with the `compose.yaml` file of this directory:

```Shell
docker compose -f samples/spring-boot-attached/compose.yaml up --build -d
```

Then, run the application:

```Shell
gradlew :samples:spring-boot-attached:bootRun
```

It waits for the office at startup (`connect-timeout`, one minute), so the two can be started in any order. Once started, convert a document:

```Shell
curl -f -o converted.pdf -F "file=@document.docx" -F format=pdf http://localhost:8080/convert
```

`format` is the extension of the target format (`pdf`, `docx`, `odt`, `xlsx`...). The application answers 400 for a request it cannot use, and 502 when the office refuses the document, fails, or cannot be reached.

When you are done:

```Shell
docker compose -f samples/spring-boot-attached/compose.yaml down
```

### Running the application in a container too

With the `app` profile, `compose.yaml` also runs the application, in a Java container of its own on the same network as the office. The application then connects to the office by its name, `office`, as it would to another machine; the port of the office is not needed outside its network, which is how a deployment keeps it. Build the jar of the application first:

```Shell
gradlew :samples:spring-boot-attached:bootJar
docker compose -f samples/spring-boot-attached/compose.yaml --profile app up --build -d
```

The application is on `http://localhost:8080` (set `APP_PORT=8081`, for example, when 8080 is taken). Stop both with:

```Shell
docker compose -f samples/spring-boot-attached/compose.yaml --profile app down
```

### How it works

- `jodconverter.attached.enabled`, `host-name` and `port-numbers` in `src/main/resources/application.yml` are all the configuration: the starter creates an `AttachedOfficeManager` that connects to the office, and a `DocumentConverter` that converts with it. The controller uses it exactly as it would use a converter of an office that the application starts itself. `OFFICE_HOST` and `OFFICE_PORT` give the address of the office when it is elsewhere.
- `load-document-mode: remote`: the office does not see the files of the application, so the documents are sent through the connection, and the converted ones come back through it. It is what the default (`auto`) does with an attached manager.
- `office/Dockerfile` is the office: Debian with LibreOffice without its graphical interface, the fonts, a UTF-8 locale, an init process, and a user that is not root. Its LibreOffice comes from trixie-backports: the version of Debian trixie itself (25.2.3) crashes when it loads a text, a presentation or a drawing sent through the connection.
- The office listens on port 2002 of its container, published as 2050 on this machine, so that it does not collide with the offices that the other samples start on 2002 and 2003, nor with those of the tests of JODConverter, on ports from 2001 to 2016 and on 2099.

### What to know about an attached office

- **JODConverter does not restart it.** The attached manager reconnects when the office comes back, but it does not start it: `restart: unless-stopped` in `compose.yaml` does that when the office exits.
- **The port controls the office.** Whoever can connect to it can make the office open, write and run what it wants in its container. `compose.yaml` publishes it on this machine only; in a deployment, keep it on a private network of the applications that use it, and never publish it.
- **One connection, one conversion at a time.** Each port is one office process, which converts one document at a time. For more, run more office containers and list their ports (`port-numbers: 2050,2051`), or use the [remote sample](../spring-boot-remote) with a conversion server.
- **Docker Desktop is slow at this.** Through the published port of Docker Desktop on Windows, a conversion took 15 to 30 seconds where the same one took 0.5 to 2.5 seconds with the `app` profile, between two containers of the same network: the office reads the document through the connection in many small calls, and each one crosses the port forwarding of Docker Desktop. Between containers, and on Linux, this cost is gone.

### The tests

The tests start the LibreOffice of the machine themselves, on a free port and with a profile of their own, as the container would; the application only connects to it. They need LibreOffice installed, as the tests of the other samples do.
