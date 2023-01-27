package org.jodconverter.sample.rest;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringBootRestApplicationTest {
  @Test
  void shouldStartApplication() {
    // we do not need to do anything else here. This is just a smoke test verifying the entire boot
    // configuration works, all beans can be wired and the app starts as expected
    assertThat("smoke test").isEqualTo("smoke test");
  }
}
