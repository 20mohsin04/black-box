# 0001: Handle JSON-RPC directly, not through the MCP Java SDK

Date: 2026-11-11. Stage 0.

## Context

Black Box sits between an agent and a tool server and forwards MCP messages.
MCP messages are JSON-RPC 2.0. Over the stdio transport each message is one
JSON object on one line. We had to choose how the gateway, the sample tool
server and the scripted client read and write those messages.

State of the official Java SDK when this was decided:

- Stable release v2.1.0 speaks protocol version 2025-11-25 (handshake-based).
- Protocol version 2026-07-28 is only in pre-release v2.2.0-M1, server side
  only. There is no client for it yet.
- It brings in Project Reactor, Jackson and SLF4J.

## Options

**A. SDK everywhere.** Least code, and conformance-tested. But the SDK is made
for building a client or a server, not something that passes messages through
the middle. It hides the wire format, uses a reactive style, adds heavy
dependencies, and is legacy-only unless we depend on a pre-release.

**B. Direct JSON-RPC everywhere, Jackson for JSON parsing only.** We own and
can explain every message. A gateway that treats each line as generic JSON-RPC
can carry both protocol versions. Few dependencies. Costs more code and tests,
and nothing independent catches a misreading of the spec.

**C. Direct in the gateway, SDK for the sample server.** Gateway as in B, tested
against an independent implementation. But two styles in one repository, and
the demo would be tied to the legacy version or a pre-release.

## Decision

Option B.

## Reason

- The gateway is a pass-through. Reading lines and matching replies to requests
  by `id` is the engineering the project is meant to show.
- Jonas must be able to explain the wire format in an interview.
- The project brief asks for as few dependencies as practical.
- The SDK cannot yet act as a client for the current protocol version.

Jackson is used because writing a JSON parser adds risk and shows nothing about
this project.

## Consequence

Our reading of the spec is not checked by another implementation until a real
agent and real servers run through the gateway (Stage 7). Tests should quote
message shapes from the specification, not from memory.
