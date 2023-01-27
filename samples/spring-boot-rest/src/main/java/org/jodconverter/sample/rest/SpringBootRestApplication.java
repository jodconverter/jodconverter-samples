package org.jodconverter.sample.rest;

import java.util.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Main application. */
@SpringBootApplication
public class SpringBootRestApplication {

  /**
   * Main entry point of the application.
   *
   * @param args Command line arguments.
   */
  public static void main(final String[] args) {
    SpringApplication.run(SpringBootRestApplication.class, args);
  }

  @Configuration
  static class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
      final Info info =
          new Info()
              .title("JODConverter REST API")
              .description(
                  "JODConverter REST API for Remote conversion. JODConverter automates conversions between office document formats using LibreOffice or Apache OpenOffice.")
              .version("0.1")
              .termsOfService("Terms of service")
              .license(
                  new License()
                      .name("Apache License Version 2.0")
                      .url("https://www.apache.org/licenses/LICENSE-2.0"));

      Server apiServer = new Server();
      apiServer.setDescription("local");
      apiServer.setUrl("/");

      List<Server> servers = new ArrayList<>();
      servers.add(apiServer);

      return new OpenAPI().servers(servers).info(info);
    }
  }
}
