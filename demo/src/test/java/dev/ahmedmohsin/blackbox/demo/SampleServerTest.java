package dev.ahmedmohsin.blackbox.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Feeds single lines to the server in-process, so each protocol rule is checked on its own. */
class SampleServerTest {

  private static final String META =
      "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
          + "\"io.modelcontextprotocol/clientCapabilities\":{}}";

  private final ObjectMapper json = new ObjectMapper();

  @TempDir Path temp;
  private Path sandboxFolder;
  private SampleServer server;

  @BeforeEach
  void setUp() throws IOException {
    sandboxFolder = Files.createDirectory(temp.resolve("sandbox"));
    server = new SampleServer(new Sandbox(sandboxFolder));
  }

  private JsonNode send(String line) throws IOException {
    return json.readTree(server.handleLine(line).orElseThrow());
  }

  private JsonNode request(String method, String extraParams) throws IOException {
    String params = extraParams.isEmpty() ? META : extraParams + "," + META;
    return send(
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"" + method + "\",\"params\":{" + params + "}}");
  }

  private JsonNode callTool(String name, String arguments) throws IOException {
    return request("tools/call", "\"name\":\"" + name + "\",\"arguments\":" + arguments)
        .path("result");
  }

  @Test
  void discoverReportsVersionAndToolsCapability() throws IOException {
    JsonNode reply = request("server/discover", "");
    assertEquals(7, reply.path("id").asInt());
    JsonNode result = reply.path("result");
    assertEquals("complete", result.path("resultType").asText());
    assertEquals("2026-07-28", result.path("supportedVersions").path(0).asText());
    assertTrue(result.path("capabilities").has("tools"));
    assertTrue(result.has("ttlMs"));
    assertEquals("public", result.path("cacheScope").asText());
  }

  @Test
  void listsFourToolsWithSchemas() throws IOException {
    JsonNode tools = request("tools/list", "").path("result").path("tools");
    assertEquals(4, tools.size());
    for (JsonNode tool : tools) {
      assertEquals("object", tool.path("inputSchema").path("type").asText());
    }
  }

  @Test
  void echoesStringIds() throws IOException {
    JsonNode reply =
        send(
            "{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"tools/list\",\"params\":{"
                + META
                + "}}");
    assertEquals("abc", reply.path("id").asText());
  }

  @Test
  void writesThenReadsAFile() throws IOException {
    JsonNode written = callTool("write_file", "{\"path\":\"dir/a.txt\",\"content\":\"hi\"}");
    assertFalse(written.path("isError").asBoolean());
    assertEquals("hi", Files.readString(sandboxFolder.resolve("dir/a.txt")));

    JsonNode read = callTool("read_file", "{\"path\":\"dir/a.txt\"}");
    assertEquals("hi", read.path("content").path(0).path("text").asText());
  }

  @Test
  void deleteRemovesTheFile() throws IOException {
    Files.writeString(sandboxFolder.resolve("gone.txt"), "x");
    assertFalse(callTool("delete_file", "{\"path\":\"gone.txt\"}").path("isError").asBoolean());
    assertFalse(Files.exists(sandboxFolder.resolve("gone.txt")));
  }

  @Test
  void pathOutsideSandboxIsAToolErrorAndTouchesNothing() throws IOException {
    Path victim = Files.writeString(temp.resolve("victim.txt"), "keep me");

    assertTrue(callTool("delete_file", "{\"path\":\"../victim.txt\"}").path("isError").asBoolean());
    assertTrue(
        callTool("write_file", "{\"path\":\"" + victim + "\",\"content\":\"x\"}")
            .path("isError")
            .asBoolean());
    assertEquals("keep me", Files.readString(victim));
  }

  @Test
  void missingArgumentIsAToolError() throws IOException {
    assertTrue(callTool("read_file", "{}").path("isError").asBoolean());
  }

  @Test
  void runCommandRunsAllowedProgramsInTheSandbox() throws IOException {
    Files.writeString(sandboxFolder.resolve("seen.txt"), "x");
    JsonNode result = callTool("run_command", "{\"command\":\"ls\"}");
    assertFalse(result.path("isError").asBoolean());
    assertTrue(result.path("content").path(0).path("text").asText().contains("seen.txt"));
  }

  @Test
  void runCommandRefusesOtherProgramsAndOutsidePaths() throws IOException {
    Files.writeString(sandboxFolder.resolve("seen.txt"), "x");
    assertTrue(
        callTool("run_command", "{\"command\":\"rm -rf seen.txt\"}").path("isError").asBoolean());
    assertTrue(Files.exists(sandboxFolder.resolve("seen.txt")));
    assertTrue(
        callTool("run_command", "{\"command\":\"cat /etc/passwd\"}").path("isError").asBoolean());
    assertTrue(callTool("run_command", "{\"command\":\"cat ../x\"}").path("isError").asBoolean());
  }

  @Test
  void unknownToolIsAProtocolError() throws IOException {
    JsonNode reply = request("tools/call", "\"name\":\"format_disk\",\"arguments\":{}");
    assertEquals(-32602, reply.path("error").path("code").asInt());
    assertFalse(reply.has("result"));
  }

  @Test
  void requestWithoutMetaIsInvalidParams() throws IOException {
    JsonNode reply = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
    assertEquals(-32602, reply.path("error").path("code").asInt());
  }

  @Test
  void otherProtocolVersionIsRejectedWithTheSupportedList() throws IOException {
    JsonNode reply =
        send(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"_meta\":{"
                + "\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\","
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}}}");
    JsonNode error = reply.path("error");
    assertEquals(-32022, error.path("code").asInt());
    assertEquals("2026-07-28", error.path("data").path("supported").path(0).asText());
    assertEquals("1900-01-01", error.path("data").path("requested").asText());
  }

  @Test
  void legacyInitializeGetsAnErrorNamingTheSupportedVersion() throws IOException {
    JsonNode reply =
        send(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}}");
    assertTrue(reply.path("error").path("message").asText().contains("2026-07-28"));
  }

  @Test
  void unknownMethodIsMethodNotFound() throws IOException {
    assertEquals(-32601, request("resources/list", "").path("error").path("code").asInt());
  }

  @Test
  void notificationsGetNoReply() {
    assertTrue(
        server
            .handleLine(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                    + "\"params\":{\"requestId\":7}}")
            .isEmpty());
  }

  @Test
  void brokenJsonIsAParseError() throws IOException {
    assertEquals(-32700, send("{not json").path("error").path("code").asInt());
  }

  @Test
  void repliesAreSingleLines() {
    // The stdio transport forbids line breaks inside a message.
    String reply =
        server
            .handleLine(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{"
                    + META
                    + "}}")
            .orElseThrow();
    assertFalse(reply.contains("\n"));
  }
}
