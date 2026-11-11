package dev.ahmedmohsin.blackbox.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A smoke test: its job is to prove JUnit 5 is wired into the build, so a broken test set-up shows
 * up now and not in Stage 1.
 */
class MainTest {

  @Test
  void bannerSaysNothingIsBuiltYet() {
    assertTrue(Main.banner().contains("no proxy yet"));
  }
}
