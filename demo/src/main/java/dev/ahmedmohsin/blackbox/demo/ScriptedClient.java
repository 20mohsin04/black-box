package dev.ahmedmohsin.blackbox.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plays the part of an agent: runs a fixed list of tool calls against a server and checks each
 * reply.
 *
 * <p>The server is whatever command it is given, so from Stage 1 the same scenario can run through
 * the gateway and the results can be compared.
 */
public final class ScriptedClient {

  private static final String NOTE = "hello from the scripted agent\n";

  private final McpStdioClient client;
  private final PrintStream log;
  private int step = 0;
  private boolean allPassed = true;

  private ScriptedClient(McpStdioClient client, PrintStream log) {
    this.client = client;
    this.log = log;
  }

  public static void main(String[] args) throws IOException {
    List<String> serverCommand;
    if (args.length == 2 && args[0].equals("--sandbox")) {
      serverCommand = sampleServerCommand(Path.of(args[1]));
    } else if (args.length >= 2 && args[0].equals("--")) {
      serverCommand = Arrays.asList(args).subList(1, args.length);
    } else {
      System.err.println("usage: ScriptedClient --sandbox <folder>   (starts the sample server)");
      System.err.println("       ScriptedClient -- <server command...>");
      System.exit(2);
      return;
    }
    System.exit(runScenario(serverCommand, System.out) ? 0 : 1);
  }

  /** The command that starts the sample server with the same Java and classpath as this JVM. */
  static List<String> sampleServerCommand(Path sandbox) {
    return List.of(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        System.getProperty("java.class.path"),
        SampleServer.class.getName(),
        sandbox.toString());
  }

  /** Runs the whole scenario. Returns true only if every step got the reply it expected. */
  static boolean runScenario(List<String> serverCommand, PrintStream log) throws IOException {
    try (McpStdioClient client = new McpStdioClient(serverCommand)) {
      ScriptedClient scenario = new ScriptedClient(client, log);
      scenario.run();
      log.println(scenario.allPassed ? "Scenario passed." : "Scenario FAILED.");
      return scenario.allPassed;
    }
  }

  private void run() throws IOException {
    JsonNode discovered = client.request("server/discover", client.newParams());
    List<String> versions = new ArrayList<>();
    discovered.path("result").path("supportedVersions").forEach(v -> versions.add(v.asText()));
    check("server/discover", versions.contains(SampleServer.PROTOCOL_VERSION), versions.toString());

    JsonNode listed = client.request("tools/list", client.newParams());
    List<String> names = new ArrayList<>();
    listed.path("result").path("tools").forEach(tool -> names.add(tool.path("name").asText()));
    check(
        "tools/list",
        names.equals(List.of("read_file", "write_file", "delete_file", "run_command")),
        names.toString());

    expectOk("write_file notes.txt", call("write_file", "path", "notes.txt", "content", NOTE));

    JsonNode read = call("read_file", "path", "notes.txt");
    check("read_file notes.txt", !isError(read) && text(read).equals(NOTE), text(read).strip());

    JsonNode ls = call("run_command", "command", "ls");
    check("run_command ls", !isError(ls) && text(ls).contains("notes.txt"), text(ls).strip());

    // The next two must be refused by the server itself, with no gateway in front of it.
    expectToolError(
        "write_file ../escape.txt", call("write_file", "path", "../escape.txt", "content", "x"));
    expectToolError(
        "run_command rm -rf notes.txt", call("run_command", "command", "rm -rf notes.txt"));

    expectOk("delete_file notes.txt", call("delete_file", "path", "notes.txt"));
    expectToolError("read_file notes.txt (deleted)", call("read_file", "path", "notes.txt"));

    // A tool that does not exist is a protocol error (JSON-RPC "error"), not an isError result.
    JsonNode unknown = call("format_disk");
    check(
        "tools/call format_disk",
        unknown.path("error").path("code").asInt() == SampleServer.INVALID_PARAMS,
        unknown.path("error").path("message").asText());
  }

  /** Calls a tool. The arguments are given as name, value, name, value. */
  private JsonNode call(String tool, String... namesAndValues) throws IOException {
    ObjectNode params = client.newParams();
    params.put("name", tool);
    ObjectNode arguments = params.putObject("arguments");
    for (int i = 0; i < namesAndValues.length; i += 2) {
      arguments.put(namesAndValues[i], namesAndValues[i + 1]);
    }
    return client.request("tools/call", params);
  }

  private static boolean isError(JsonNode reply) {
    // A reply with no "result" at all is a protocol error, which also counts as not ok.
    return !reply.has("result") || reply.path("result").path("isError").asBoolean(false);
  }

  private static String text(JsonNode reply) {
    return reply.path("result").path("content").path(0).path("text").asText();
  }

  private void expectOk(String label, JsonNode reply) {
    check(label, !isError(reply), text(reply));
  }

  private void expectToolError(String label, JsonNode reply) {
    boolean refused = reply.has("result") && reply.path("result").path("isError").asBoolean(false);
    check(label + " is refused", refused, text(reply));
  }

  private void check(String label, boolean passed, String detail) {
    step++;
    allPassed &= passed;
    log.printf("[%2d] %-4s %s -> %s%n", step, passed ? "ok" : "FAIL", label, detail);
  }
}
