package org.jodconverter.sample.remote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * An application that converts documents without an office of its own: the JODConverter starter
 * creates a converter that sends them to a Collabora Online server (jodconverter.remote.* in
 * application.yml).
 */
@SpringBootApplication
public class SpringBootRemoteApplication {

  /**
   * Main entry point of the application.
   *
   * @param args Command line arguments.
   */
  public static void main(final String[] args) {
    SpringApplication.run(SpringBootRemoteApplication.class, args);
  }
}
