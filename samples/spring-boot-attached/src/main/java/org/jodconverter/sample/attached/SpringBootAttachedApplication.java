package org.jodconverter.sample.attached;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * An application that converts with an office it does not start: the JODConverter starter connects
 * to an office process that runs apart, in a container, and sends it the documents
 * (jodconverter.attached.* in application.yml).
 */
@SpringBootApplication
public class SpringBootAttachedApplication {

  /**
   * Main entry point of the application.
   *
   * @param args Command line arguments.
   */
  public static void main(final String[] args) {
    SpringApplication.run(SpringBootAttachedApplication.class, args);
  }
}
