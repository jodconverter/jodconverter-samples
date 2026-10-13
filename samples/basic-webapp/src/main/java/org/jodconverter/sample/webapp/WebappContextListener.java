package org.jodconverter.sample.webapp;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.jodconverter.core.office.OfficeException;

/**
 * Starts the office manager when the web application starts, and stops it when the application
 * stops: the most important thing a web application that uses JODConverter has to do.
 */
public class WebappContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(final ServletContextEvent event) {

    try {
      WebappContext.init(event.getServletContext());
    } catch (OfficeException e) {
      throw new IllegalStateException("The office manager could not be started", e);
    }
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    try {
      WebappContext.destroy(event.getServletContext());
    } catch (OfficeException e) {
      throw new IllegalStateException("The office manager could not be stopped", e);
    }
  }
}
