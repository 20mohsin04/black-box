package dev.ahmedmohsin.blackbox.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ahmedmohsin.blackbox.demo.SampleTools.ToolResult;
import dev.ahmedmohsin.blackbox.demo.SampleTools.UnknownToolException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A small MCP tool server that speaks protocol version 2026-07-28 over stdio.
 *
 * <p>stdio transport: one JSON-RPC message per line on stdin, one per line on stdout, and nothing
 * else on stdout. Logging goes to stderr. The protocol is stateless: there is no handshake, and
 * each request carries its own protocol version and client capabilities in {@code params._meta}.
 *
 * <p>Requests are handled one at a time on one thread. That is enough for a demo server and keeps
 * replies in the order the requests arrived.
 */
public final class SampleServer {

  static final String PROTOCOL_VERSION = "2026-07-28";

  static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
  static final String META_CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
  static final String META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
  static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

  // JSON-RPC 2.0 error codes, plus the one MCP defines for a version it does not speak.
  static final int PARSE_ERROR = -32700;
  static final int INVALID_REQUEST = -32600;
  static final int METHOD_NOT_FOUND = -32601;
  static final int INVALID_PARAMS = -32602;
  static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

  private final ObjectMapper json = new ObjectMapper();
  private final SampleTools tools;

  SampleServer(Sandbox sandbox) {
    this.tools = new SampleTools(sandbox, json);
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("usage: SampleServer <sandbox-folder>");
      System.exit(2);
    }
    Path folder = Path.of(args[0]);
    // The folder must already exist. Creating whatever path we are handed would make it too easy
    // to point the server at a real folder by mistake.
    if (!Files.isDirectory(folder)) {
      System.err.println("sandbox folder does not exist: " + folder);
      System.exit(2);
    }
    SampleServer server = new SampleServer(new Sandbox(folder));

    BufferedReader in =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
    String line;
    // readLine() returns null when the client closes our stdin. That is the shutdown signal.
    while ((line = in.readLine()) != null) {
      if (line.isBlank()) {
        continue;
      }
      Optional<String> reply = server.handleLine(line);
      if (reply.isPresent()) {
        out.print(reply.get());
        out.print('\n');
        out.flush();
      }
    }
  }

  /**
   * Handles one line from the client. Returns the reply line, or empty when no reply is due
   * (notifications get none).
   */
  Optional<String> handleLine(String line) {
    JsonNode message;
    try {
      message = json.readTree(line);
    } catch (JsonProcessingException e) {
      return Optional.of(error(null, PARSE_ERROR, "Parse error", null));
    }
    if (message == null
        || !message.isObject()
        || !"2.0".equals(message.path("jsonrpc").asText(null))) {
      return Optional.of(error(null, INVALID_REQUEST, "Not a JSON-RPC 2.0 message", null));
    }

    JsonNode id = message.get("id");
    JsonNode method = message.get("method");
    if (method == null || !method.isTextual()) {
      return Optional.of(error(usableId(id), INVALID_REQUEST, "Missing method", null));
    }
    if (id == null) {
      // A notification, such as notifications/cancelled. Requests here finish before the next
      // line is read, so there is never anything left to cancel.
      return Optional.empty();
    }
    if (usableId(id) == null) {
      return Optional.of(error(null, INVALID_REQUEST, "id must be a string or an integer", null));
    }

    return Optional.of(handleRequest(id, method.asText(), message.path("params")));
  }

  private String handleRequest(JsonNode id, String method, JsonNode params) {
    if (method.equals("initialize")) {
      // A client from before 2026-07-28 opens with a handshake. It has no way to move forward, so
      // the spec asks us to name the versions we do speak in the error.
      return error(
          id,
          METHOD_NOT_FOUND,
          "initialize is not supported: this server speaks MCP " + PROTOCOL_VERSION + " only",
          supportedVersions(null));
    }

    JsonNode meta = params.path("_meta");
    JsonNode version = meta.get(META_PROTOCOL_VERSION);
    JsonNode capabilities = meta.get(META_CLIENT_CAPABILITIES);
    if (version == null
        || !version.isTextual()
        || capabilities == null
        || !capabilities.isObject()) {
      return error(
          id,
          INVALID_PARAMS,
          "params._meta must carry " + META_PROTOCOL_VERSION + " and " + META_CLIENT_CAPABILITIES,
          null);
    }
    if (!PROTOCOL_VERSION.equals(version.asText())) {
      return error(
          id,
          UNSUPPORTED_PROTOCOL_VERSION,
          "Unsupported protocol version",
          supportedVersions(version.asText()));
    }

    return switch (method) {
      case "server/discover" -> discover(id);
      case "tools/list" -> listTools(id);
      case "tools/call" -> callTool(id, params);
      default -> error(id, METHOD_NOT_FOUND, "Method not found: " + method, null);
    };
  }

  private String discover(JsonNode id) {
    ObjectNode result = completeResult();
    result.putArray("supportedVersions").add(PROTOCOL_VERSION);
    result.putObject("capabilities").putObject("tools");
    addCacheHints(result);
    return response(id, result);
  }

  private String listTools(JsonNode id) {
    ObjectNode result = completeResult();
    result.set("tools", tools.definitions());
    addCacheHints(result);
    return response(id, result);
  }

  private String callTool(JsonNode id, JsonNode params) {
    JsonNode name = params.get("name");
    if (name == null || !name.isTextual()) {
      return error(id, INVALID_PARAMS, "tools/call needs a string \"name\"", null);
    }
    JsonNode arguments = params.path("arguments");
    if (!arguments.isObject() && !arguments.isMissingNode()) {
      return error(id, INVALID_PARAMS, "\"arguments\" must be an object", null);
    }

    ToolResult outcome;
    try {
      outcome = tools.call(name.asText(), arguments);
    } catch (UnknownToolException e) {
      // The request itself is wrong, so this is a JSON-RPC error and not an isError result.
      return error(id, INVALID_PARAMS, e.getMessage(), null);
    }
    ObjectNode result = completeResult();
    result.putArray("content").addObject().put("type", "text").put("text", outcome.text());
    result.put("isError", outcome.isError());
    return response(id, result);
  }

  private ObjectNode completeResult() {
    ObjectNode result = json.createObjectNode();
    result.put("resultType", "complete");
    // With no handshake, the server names itself on every result.
    ObjectNode serverInfo = result.putObject("_meta").putObject(META_SERVER_INFO);
    serverInfo.put("name", "black-box-sample-server");
    serverInfo.put("version", "0.1.0");
    return result;
  }

  // The spec requires caching hints on discover and list results. The tool list never changes
  // while the server runs and is the same for every caller.
  private static void addCacheHints(ObjectNode result) {
    result.put("ttlMs", 300_000);
    result.put("cacheScope", "public");
  }

  private ObjectNode supportedVersions(String requested) {
    ObjectNode data = json.createObjectNode();
    data.putArray("supported").add(PROTOCOL_VERSION);
    if (requested != null) {
      data.put("requested", requested);
    }
    return data;
  }

  /** MCP allows string or integer ids only. Returns null for anything else. */
  private static JsonNode usableId(JsonNode id) {
    return id != null && (id.isTextual() || id.isIntegralNumber()) ? id : null;
  }

  private String response(JsonNode id, ObjectNode result) {
    ObjectNode message = json.createObjectNode();
    message.put("jsonrpc", "2.0");
    message.set("id", id);
    message.set("result", result);
    return message.toString();
  }

  private String error(JsonNode id, int code, String text, JsonNode data) {
    ObjectNode message = json.createObjectNode();
    message.put("jsonrpc", "2.0");
    if (id != null) {
      message.set("id", id);
    }
    ObjectNode error = message.putObject("error");
    error.put("code", code);
    error.put("message", text);
    if (data != null) {
      error.set("data", data);
    }
    return message.toString();
  }
}
