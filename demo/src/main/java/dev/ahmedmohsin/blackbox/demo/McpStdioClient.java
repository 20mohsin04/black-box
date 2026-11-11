package dev.ahmedmohsin.blackbox.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The client end of the stdio transport: starts a server as a child process, writes one request per
 * line to its stdin and reads replies from its stdout.
 *
 * <p>It sends one request at a time and waits for the reply, which is all the scripted scenario
 * needs.
 */
final class McpStdioClient implements AutoCloseable {

  private final ObjectMapper json = new ObjectMapper();
  private final Process server;
  private final BufferedWriter toServer;
  private final BufferedReader fromServer;
  private int nextId = 1;

  McpStdioClient(List<String> serverCommand) throws IOException {
    server =
        new ProcessBuilder(serverCommand)
            // stderr is the server's log channel. Passing it straight through also stops the
            // server blocking on a full stderr pipe that nobody reads.
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    toServer =
        new BufferedWriter(
            new OutputStreamWriter(server.getOutputStream(), StandardCharsets.UTF_8));
    fromServer =
        new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
  }

  ObjectNode newParams() {
    return json.createObjectNode();
  }

  /** Sends a request and returns the whole reply message (with either "result" or "error"). */
  JsonNode request(String method, ObjectNode params) throws IOException {
    int id = nextId++;

    // No handshake in 2026-07-28: every request states its version and capabilities itself.
    ObjectNode meta = params.putObject("_meta");
    meta.put(SampleServer.META_PROTOCOL_VERSION, SampleServer.PROTOCOL_VERSION);
    meta.putObject(SampleServer.META_CLIENT_INFO)
        .put("name", "black-box-scripted-client")
        .put("version", "0.1.0");
    meta.putObject(SampleServer.META_CLIENT_CAPABILITIES);

    ObjectNode message = json.createObjectNode();
    message.put("jsonrpc", "2.0");
    message.put("id", id);
    message.put("method", method);
    message.set("params", params);

    // Jackson's compact output has no line breaks, so one message is always exactly one line.
    toServer.write(message.toString());
    toServer.write('\n');
    toServer.flush();

    String line;
    while ((line = fromServer.readLine()) != null) {
      JsonNode reply = json.readTree(line);
      // The server may also write notifications on the same stream. Skip anything that is not
      // the reply to this request.
      if (reply.path("id").isInt() && reply.path("id").asInt() == id) {
        return reply;
      }
    }
    throw new IOException("server closed its output before replying to " + method);
  }

  /** Shuts down the way the spec asks: close the server's stdin, wait, and only then force it. */
  @Override
  public void close() throws IOException {
    toServer.close();
    try {
      if (!server.waitFor(5, TimeUnit.SECONDS)) {
        server.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      server.destroyForcibly();
    }
  }
}
