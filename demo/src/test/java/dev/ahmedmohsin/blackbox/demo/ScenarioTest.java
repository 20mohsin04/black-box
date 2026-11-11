package dev.ahmedmohsin.blackbox.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end: starts the sample server as a real child process and runs the scripted client against
 * it over stdin and stdout. This is the Stage 0 "done when" line as a test.
 */
class ScenarioTest {

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void scriptedClientCompletesItsScenarioAgainstTheSampleServer(@TempDir Path sandbox)
      throws IOException {
    ByteArrayOutputStream log = new ByteArrayOutputStream();
    boolean passed =
        ScriptedClient.runScenario(
            ScriptedClient.sampleServerCommand(sandbox),
            new PrintStream(log, true, StandardCharsets.UTF_8));
    assertTrue(passed, log.toString(StandardCharsets.UTF_8));
  }
}
