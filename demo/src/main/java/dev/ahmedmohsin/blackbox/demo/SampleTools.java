package dev.ahmedmohsin.blackbox.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ahmedmohsin.blackbox.demo.Sandbox.SandboxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** The four tools the sample server offers: read, write and delete a file, and run a command. */
final class SampleTools {

  /**
   * What a tool call produced. A failed tool call is still a normal result with {@code isError}
   * set, so the agent can read the message and try again. See "Error Handling" in the MCP tools
   * specification.
   */
  record ToolResult(String text, boolean isError) {
    static ToolResult ok(String text) {
      return new ToolResult(text, false);
    }

    static ToolResult failed(String text) {
      return new ToolResult(text, true);
    }
  }

  /** The name does not match any tool. This is a protocol error, not a tool failure. */
  static final class UnknownToolException extends Exception {
    UnknownToolException(String name) {
      super("Unknown tool: " + name);
    }
  }

  // run_command never starts a shell and only runs these read-only programs. A demo server that
  // could run "rm -rf" for real would break the rule that demos never touch real files.
  private static final Set<String> ALLOWED_PROGRAMS = Set.of("ls", "cat", "echo", "pwd", "wc");

  private static final int MAX_OUTPUT_BYTES = 64 * 1024;
  private static final long COMMAND_TIMEOUT_SECONDS = 5;

  private final Sandbox sandbox;
  private final ObjectMapper json;

  SampleTools(Sandbox sandbox, ObjectMapper json) {
    this.sandbox = sandbox;
    this.json = json;
  }

  /**
   * The tool list in the shape {@code tools/list} returns. The order is fixed, as the spec asks.
   */
  ArrayNode definitions() {
    ArrayNode tools = json.createArrayNode();
    tools.add(
        definition("read_file", "Read a text file inside the sandbox.", List.of("path"), "path"));
    tools.add(
        definition(
            "write_file",
            "Create or replace a text file inside the sandbox.",
            List.of("path", "content"),
            "path",
            "content"));
    tools.add(
        definition("delete_file", "Delete a file inside the sandbox.", List.of("path"), "path"));
    tools.add(
        definition(
            "run_command",
            "Run a command in the sandbox folder. Only "
                + String.join(", ", ALLOWED_PROGRAMS.stream().sorted().toList())
                + " are available.",
            List.of("command"),
            "command"));
    return tools;
  }

  private ObjectNode definition(
      String name, String description, List<String> required, String... stringProperties) {
    ObjectNode schema = json.createObjectNode();
    schema.put("type", "object");
    ObjectNode properties = schema.putObject("properties");
    for (String property : stringProperties) {
      properties.putObject(property).put("type", "string");
    }
    ArrayNode requiredNode = schema.putArray("required");
    required.forEach(requiredNode::add);

    ObjectNode tool = json.createObjectNode();
    tool.put("name", name);
    tool.put("description", description);
    tool.set("inputSchema", schema);
    return tool;
  }

  ToolResult call(String name, JsonNode arguments) throws UnknownToolException {
    try {
      return switch (name) {
        case "read_file" -> readFile(text(arguments, "path"));
        case "write_file" -> writeFile(text(arguments, "path"), text(arguments, "content"));
        case "delete_file" -> deleteFile(text(arguments, "path"));
        case "run_command" -> runCommand(text(arguments, "command"));
        default -> throw new UnknownToolException(name);
      };
    } catch (SandboxException | IllegalArgumentException e) {
      return ToolResult.failed(e.getMessage());
    } catch (NoSuchFileException e) {
      return ToolResult.failed("No such file: " + sandbox.root().relativize(Path.of(e.getFile())));
    } catch (IOException e) {
      return ToolResult.failed("I/O error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ToolResult.failed("Interrupted while running the command");
    }
  }

  private static String text(JsonNode arguments, String field) {
    JsonNode value = arguments.get(field);
    if (value == null || !value.isTextual()) {
      throw new IllegalArgumentException("Argument \"" + field + "\" must be a string");
    }
    return value.asText();
  }

  private ToolResult readFile(String path) throws SandboxException, IOException {
    return ToolResult.ok(Files.readString(sandbox.resolve(path), StandardCharsets.UTF_8));
  }

  private ToolResult writeFile(String path, String content) throws SandboxException, IOException {
    Path target = sandbox.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content, StandardCharsets.UTF_8);
    return ToolResult.ok("Wrote " + content.length() + " characters to " + path);
  }

  private ToolResult deleteFile(String path) throws SandboxException, IOException {
    Files.delete(sandbox.resolve(path));
    return ToolResult.ok("Deleted " + path);
  }

  private ToolResult runCommand(String command) throws IOException, InterruptedException {
    List<String> words = List.of(command.trim().split("\\s+"));
    String program = words.get(0);
    if (!ALLOWED_PROGRAMS.contains(program)) {
      return ToolResult.failed("Command not available on this server: " + program);
    }
    for (String argument : words.subList(1, words.size())) {
      // The programs take paths as plain arguments, so the path rules are applied here too.
      if (argument.startsWith("/") || argument.startsWith("~") || argument.contains("..")) {
        return ToolResult.failed("Argument points outside the sandbox: " + argument);
      }
    }

    Process process =
        new ProcessBuilder(words)
            .directory(sandbox.root().toFile())
            .redirectErrorStream(true)
            .start();
    // Nothing to send. Closing stdin makes a program that waits for input (cat) stop at once.
    process.getOutputStream().close();
    byte[] output = process.getInputStream().readNBytes(MAX_OUTPUT_BYTES);
    if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      return ToolResult.failed("Command did not finish in " + COMMAND_TIMEOUT_SECONDS + " seconds");
    }
    String text = new String(output, StandardCharsets.UTF_8);
    return process.exitValue() == 0
        ? ToolResult.ok(text)
        : ToolResult.failed("Exit code " + process.exitValue() + ": " + text);
  }
}
