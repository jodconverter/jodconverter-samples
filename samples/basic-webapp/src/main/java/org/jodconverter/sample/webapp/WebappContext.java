package org.jodconverter.sample.webapp;

import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;

/**
 * What the web application shares between its requests: one office manager, started with the
 * application and stopped with it, and one converter. The office manager takes care of the
 * requests that arrive together.
 */
public class WebappContext {

  private static final String PARAMETER_OFFICE_PORT = "office.port";
  private static final String PARAMETER_OFFICE_HOME = "office.home";
  private static final String PARAMETER_OFFICE_PROFILE = "office.profile";

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappContext.class);

  private static final String KEY = WebappContext.class.getName();

  private final OfficeManager officeManager;
  private final DocumentConverter documentConverter;

  /**
   * Creates a new WebappContext using the specified servlet context.
   *
   * @param servletContext the servlet context that contains properties used to create a JOD
   *     document converter.
   */
  public WebappContext(final ServletContext servletContext) {

    final LocalOfficeManager.Builder builder = LocalOfficeManager.builder();

    // The context parameters of web.xml, all optional: without them, the office is looked for in
    // its usual locations and started on the default port, with a new profile.
    final String officePortParam = servletContext.getInitParameter(PARAMETER_OFFICE_PORT);
    if (officePortParam != null) {
      builder.portNumbers(Integer.parseInt(officePortParam));
    }
    builder.officeHome(servletContext.getInitParameter(PARAMETER_OFFICE_HOME));
    builder.templateProfileDir(servletContext.getInitParameter(PARAMETER_OFFICE_PROFILE));

    officeManager = builder.build();
    documentConverter = LocalConverter.make(officeManager);
  }

  /**
   * Creates the context of the web application and starts its office manager.
   *
   * @param servletContext the servlet context.
   * @throws OfficeException if the office manager cannot be started.
   */
  protected static void init(final ServletContext servletContext) throws OfficeException {
    final WebappContext instance = new WebappContext(servletContext);
    servletContext.setAttribute(KEY, instance);
    instance.officeManager.start();
    LOGGER.info("Office manager started");
  }

  /**
   * Stops the office manager of the web application.
   *
   * @param servletContext the servlet context.
   * @throws OfficeException if the office manager cannot be stopped.
   */
  protected static void destroy(final ServletContext servletContext) throws OfficeException {
    final WebappContext instance = get(servletContext);
    if (instance != null) {
      instance.officeManager.stop();
      LOGGER.info("Office manager stopped");
    }
  }

  /**
   * Gets the WebappContext from the specified servlet context.
   *
   * @param servletContext the servlet context that contains the WebappContext.
   * @return the WebappContext.
   */
  public static WebappContext get(final ServletContext servletContext) {
    return (WebappContext) servletContext.getAttribute(KEY);
  }

  /**
   * Gets the office manager of the context.
   *
   * @return the context's office manager.
   */
  public OfficeManager getOfficeManager() {
    return officeManager;
  }

  /**
   * Gets the document converter of the context.
   *
   * @return the context's document converter.
   */
  public DocumentConverter getDocumentConverter() {
    return documentConverter;
  }
}
