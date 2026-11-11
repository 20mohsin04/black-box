# Black Box

A flight recorder and safety gateway for AI agents.

Black Box sits between an AI agent and the tools it uses. It records every
action in a tamper-evident log, lets you replay a session step by step, and
blocks or pauses dangerous actions before they run.

**Status:** Stage 0 of 8. The gateway itself is not built yet, so none of the
features above exist. What exists: the build, tests and CI, a sample MCP tool
server that only touches a sandbox folder, and a scripted client that plays
the part of an agent. See [docs/plan.md](docs/plan.md).

    ./gradlew build            # compile, test, check formatting
    ./gradlew :demo:scenario   # run the scripted client against the sample server

This README will be rewritten as stages are completed, and will only describe
what exists and has been measured.
