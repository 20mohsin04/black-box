package dev.ahmedmohsin.blackbox.gateway;

/**
 * Entry point for the gateway.
 *
 * <p>Stage 0 only proves that the build, tests, formatting and CI work. The proxy arrives in Stage
 * 1, so for now this says what it is and exits rather than pretending to do anything.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    System.out.println(banner());
  }

  static String banner() {
    return "Black Box gateway: build skeleton only, no proxy yet (see docs/plan.md).";
  }
}
