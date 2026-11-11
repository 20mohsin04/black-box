# Black Box build plan

Eight stages. Each one ends with something that runs. Stages 1 to 3 are the
core and make a complete project on their own; everything after adds reach and
polish. At roughly one stage per week alongside exam revision, the core is
done before January and the rest follows after the exam.

## Stage 0: Foundations

Set up the Gradle project, Java 21, JUnit 5, formatting, and a GitHub Actions
workflow that builds and tests on every push. Add a tiny sample MCP tool server
in /demo (read file, write file, delete file, run command) that only touches a
sandbox folder, plus a scripted client that plays the part of an agent.

**Done when:** `./gradlew build` passes locally and in CI, and the scripted
client can call the sample server directly.

**Decision for Jonas:** use the official MCP Java SDK, or handle the JSON-RPC
messages directly.

## Stage 1: Transparent proxy

The gateway launches the real tool server as a child process and sits in the
middle: every message from the agent goes through it to the server, and every
reply goes back. Nothing is changed yet. Each message is parsed, matched to its
request, timed and printed.

**Done when:** the scripted client runs its whole scenario through the gateway
with identical results, and the gateway prints a correct list of every tool
call with its arguments, result and duration.

**What it shows:** protocol handling, process management, concurrent streams.

## Stage 2: The recorder

Every exchange is written to an append-only log before the reply is released.
Each entry stores a hash of the entry before it, so any later edit or deletion
breaks the chain. Add a `verify` command that checks a log and a `show` command
that prints a session.

**Done when:** a session can be recorded, shown and verified; a test edits one
byte of a log and `verify` reports exactly where the chain breaks; the gateway
refuses to forward if it cannot write the log.

**Decision for Jonas:** storage format (plain files of JSON lines, or SQLite).

**What it shows:** durable storage, integrity, failing closed.

## Stage 3: The rule engine

Rules are read from a file. A rule matches on the tool name and its arguments
and gives one of three outcomes: allow, deny, or hold for approval. Start with
rules that matter: paths outside the project folder, destructive commands,
and text that looks like a secret leaving the machine. A denied call gets a
proper error back so the agent knows it was refused. Every decision is logged
with the rule that caused it.

**Done when:** the demo scenario "agent tries to delete outside the sandbox" is
blocked and recorded; rules have unit tests including attempts to dodge them
(relative paths, symlinks, odd spacing); a rule file with a mistake is rejected
at start-up with a clear message.

**What it shows:** security thinking, parsing, careful testing.

## Stage 4: Approvals and the API

Add an HTTP API: list sessions, read a session's events, stream live events,
approve or reject a held action. A held action waits, with a timeout that
rejects it. Support many agent sessions at once.

**Done when:** two scripted agents run at the same time through one gateway,
one hits a held action, and approving it through the API lets it continue while
the other is unaffected.

**What it shows:** concurrency, API design, timeouts.

## Stage 5: The timeline

A React interface. A list of sessions; inside one, a timeline of actions you
can step through, with arguments, results and file changes shown as diffs.
Blocked actions stand out. Held actions show Approve and Reject buttons live.
A badge shows whether the log's chain verifies.

**Done when:** someone who has never seen the project can open a recorded
session and say what the agent did and where it was stopped.

**What it shows:** front-end work, turning raw events into something readable.

## Stage 6: Performance

Measure what the gateway costs. A benchmark harness reports added delay per
call (median, 99th percentile) and calls per second at increasing numbers of
simultaneous sessions. Find the slowest part, improve it once, and write up
before and after with the reasoning.

**Done when:** /bench produces a repeatable report and docs has a one-page
write-up of one real optimisation with measured numbers.

**What it shows:** measurement, profiling, the numbers for the CV.

## Stage 7: Real agent, replay and release

Connect a real coding agent to the gateway and record a real session. Add
replay: run a recorded session again against a new rule file to see which past
actions the new rules would have stopped. Then finish: Docker Compose, a README
with a demo GIF and architecture diagram, docs/architecture.md, and the
interview notes tidied.

**Done when:** `docker compose up` runs everything; the README demo can be
followed from a fresh clone; the project is linked from ahmedmohsin.dev.

**Final outputs:** a two-minute demo script and three CV lines based only on
what was built and measured.

## Out of scope

Authentication for multiple organisations, cloud hosting at scale, judging the
agent's reasoning with another model, and support for protocols other than MCP.
Each is a sensible later extension and none is needed for the project to do its
job.
