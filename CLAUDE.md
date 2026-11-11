# Black Box

A flight recorder and safety gateway for AI agents. It sits between an AI agent
and the tools the agent uses, records every action, lets a person replay a
session step by step, and blocks or pauses dangerous actions before they run.

## Who this is for

Jonas Mohsin (GitHub: 20mohsin04), a second-year Computer Science student
applying for 2027 software engineering placements. This is his flagship
portfolio project. Java is his strongest language; he also knows JavaScript,
React, Node.js, SQL, REST APIs, Git and Linux.

The project exists to get him hired. That sets two hard requirements:

1. He must be able to explain and defend every design decision in an interview.
2. Every claim made about the project (features, performance) must be true and
   measured on his machine.

## What it is, in one paragraph

AI agents call tools through the Model Context Protocol (MCP). Black Box is a
gateway that an agent connects to in place of the real tool server. It forwards
each request to the real server and each response back, and in between it
records the exchange in a tamper-evident log, checks the request against a set
of rules, and can refuse it or hold it for human approval. A web timeline shows
what the agent did.

## Stack

- Java 21, Gradle. Core gateway with as few dependencies as practical.
- JUnit 5 for tests. GitHub Actions for build and test on every push.
- React and TypeScript (Vite) for the timeline interface.
- Docker Compose to run the whole system with one command.

Before writing protocol code, read the current MCP specification and check what
the official Java SDK offers. MCP changes often, so do not rely on memory for
message formats or transports. Whether to use the SDK or handle the JSON-RPC
messages directly is a design decision to put to Jonas, with trade-offs.

## Repository layout

    /gateway     Java gateway: proxy, recorder, policy engine, API
    /web         React timeline interface
    /bench       Load and latency benchmarks
    /demo        Scripted demo scenarios and a sample tool server
    /docs        plan.md, architecture.md, decisions/, interview-notes.md

## How to work with Jonas

1. Follow docs/plan.md. Build one stage at a time. A stage is finished only
   when it runs, its tests pass, and its "done when" line in the plan is true.
2. Before each stage, explain in a few sentences what is being built and why it
   is designed that way.
3. When there is a real design choice, give two or three options with their
   trade-offs and a recommendation, then let Jonas choose. Record the choice in
   docs/decisions/ as a short note: context, options, decision, reason.
4. After each stage, ask Jonas three questions that check he understood it, and
   wait for his answers before moving on. Add those questions, with concise
   answers, to docs/interview-notes.md.
5. Prefer simple, readable code over clever code. Comment the why, not the what.
6. Keep commits small with clear messages. Jonas reviews and makes the commits.
7. If something will not work or is out of scope, say so plainly. No placeholder
   features, no invented benchmark numbers, no overstated README claims.
8. Jonas has a January exam and limited hours. Keep sessions focused: one stage,
   or one clear slice of a stage, per session.

## Honest positioning

Commercial and open-source tools already record and police agent tool calls.
Black Box is not presented as a new invention. It is a from-scratch build of
that kind of system, made to show backend engineering: protocol handling,
concurrency, durable storage, security rules and measured performance. The
README must say this plainly.

## Safety rules for the project itself

- No secrets in the repository. Use .env.example.
- Demo scenarios run against a throwaway sandbox folder, never real files.
- The gateway must fail closed: if recording or rule checking fails, the action
  is refused, not passed through unrecorded.
