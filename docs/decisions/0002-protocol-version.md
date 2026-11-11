# 0002: Demo speaks MCP 2026-07-28; gateway stays version-neutral

Date: 2026-11-11. Stage 0.

## Context

MCP changed shape on 28 July 2026.

- **Legacy (2025-11-25 and earlier):** a connection opens with an `initialize`
  handshake that agrees the version and capabilities once. The server may send
  its own requests to the client.
- **Modern (2026-07-28):** no handshake. Every request carries its protocol
  version and client capabilities in `_meta`. Results carry `resultType`. The
  server never sends requests; if it needs something it replies
  `input_required` and the client retries with the answer. `server/discover`
  is an optional way to ask what a server supports.

The sample tool server and scripted client must speak one of these. The gateway
forwards whatever it is given.

## Options

**A. Modern only for the demo.** Current, and less code because there is no
handshake. Risk: the real agent connected in Stage 7 may still speak legacy.

**B. Legacy only for the demo.** Matches most software deployed before mid-2026,
but builds new code on the version being replaced.

**C. Both in the demo.** Widest cover, roughly double the demo code, for no gain
in what the project shows.

## Decision

Option A. The sample server and scripted client speak 2026-07-28. The gateway
parses each line as generic JSON-RPC and does not depend on either version.

One addition: Stage 1 includes a test that sends a legacy `initialize`
handshake through the gateway and checks it arrives and returns unchanged.

## Reason

- New code should target the current specification.
- A pass-through gateway should not care which version it carries, and the
  Stage 1 test proves that early, not in Stage 7.

## Consequence

Legacy servers can send requests to the client, which modern servers cannot.
Stage 1 matching of replies to requests must therefore work in both directions,
not assume every request comes from the agent.
