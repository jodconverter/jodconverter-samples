package org.jodconverter.sample.webapp.launcher;

import java.io.File;
import java.nio.file.Files;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;

/**
 * Runs the web application in an embedded Tomcat, from its war file: what {@code gradlew
 * :samples:basic-webapp:run} starts.
 *
 * <p>The launcher is not part of the web application. The war file it deploys is the one that a
 * Tomcat, or any other Jakarta Servlet 6 container, would be given.
 */
public final class Launcher {

  /** The context path of the web application. */
  public static final String CONTEXT_PATH = "/jodconverter-sample-webapp";

  /**
   * Starts an embedded Tomcat that serves the given war file.
   *
   * @param warFile The war file of the web application.
   * @param port The HTTP port, or 0 for any free port.
   * @return The started server; {@code getConnector().getLocalPort()} gives its port.
   * @throws Exception If the server cannot be started.
   */
  public static Tomcat start(final File warFile, final int port) throws Exception {

    final Tomcat tomcat = new Tomcat();
    // The work directory of the server, with the directory where the war file is unpacked.
    final File baseDir = Files.createTempDirectory("jodconverter-sample-tomcat").toFile();
    Files.createDirectories(new File(baseDir, "webapps").toPath());
    tomcat.setBaseDir(baseDir.getAbsolutePath());
    tomcat.setPort(port);
    // Creates the HTTP connector, which Tomcat only does when asked.
    tomcat.getConnector();

    // The defaults that Tomcat gives to every application include the JSP servlet, which needs
    // the JSP engine. The application has no JSP page, so only the defaults it uses are given
    // here: the servlet that serves the static files, and the media types of the files.
    tomcat.setAddDefaultWebXmlToWebapp(false);
    final Context context = tomcat.addWebapp(CONTEXT_PATH, warFile.getAbsolutePath());
    Tomcat.addServlet(context, "default", new DefaultServlet()).setLoadOnStartup(1);
    context.addServletMapping("/", "default");
    Tomcat.addDefaultMimeTypeMappings(context);

    tomcat.start();
    return tomcat;
  }

  /**
   * Starts the server and waits until it is stopped (Ctrl+C).
   *
   * @param args The path of the war file, and optionally the HTTP port (8080 by default).
   * @throws Exception If the server cannot be started.
   */
  public static void main(final String[] args) throws Exception {

    if (args.length == 0) {
      throw new IllegalArgumentException("usage: Launcher <war file> [port]");
    }
    final Tomcat tomcat = start(new File(args[0]), args.length > 1 ? Integer.parseInt(args[1]) : 8080);

    // Stops the web application, and so its office manager, when the JVM is stopped.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    tomcat.stop();
                    tomcat.destroy();
                  } catch (LifecycleException ignored) {
                    // The JVM is going down anyway.
                  }
                },
                "stop-tomcat"));

    System.out.println(
        "JODConverter sample: http://localhost:"
            + tomcat.getConnector().getLocalPort()
            + CONTEXT_PATH
            + "/");
    tomcat.getServer().await();
  }

  private Launcher() {
    throw new AssertionError("Not instantiable");
  }
}
