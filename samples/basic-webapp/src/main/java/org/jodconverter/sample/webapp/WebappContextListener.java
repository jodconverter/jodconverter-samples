package org.jodconverter.sample.webapp;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.jodconverter.core.office.OfficeException;

/** Web context listener. */
@SuppressWarnings("PMD")
public class WebappContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(final ServletContextEvent event) {

    try {
      WebappContext.init(event.getServletContext());
    } catch (OfficeException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    try {
      WebappContext.destroy(event.getServletContext());
    } catch (OfficeException e) {
      throw new RuntimeException(e);
    }
  }
}
