# 0003: The sample server's run_command is limited to a fixed list of programs

Date: 2026-11-11. Stage 0.

## Context

The plan asks for a sample tool server with a "run command" tool that only
touches a sandbox folder. File tools can be confined by checking the path. A
command cannot: `rm -rf ~` run from inside the sandbox folder still deletes
real files. In Stage 0 the scripted client talks to the server directly, with
no gateway in front, so the server has to be safe by itself.

## Options

**A. Run any command through a shell.** Most realistic. Not safe: one wrong
scenario line, or a bug in a later gateway rule, damages real files.

**B. Fixed list of read-only programs, no shell.** The command is split on
spaces and run directly. Only `ls`, `cat`, `echo`, `pwd` and `wc` are allowed,
and arguments that are absolute, start with `~` or contain `..` are refused.
Safe. Less realistic: no pipes, quotes or redirection.

**C. Pretend to run commands and return canned output.** Safest, but then the
tool does nothing real and the demo is partly fake.

## Decision

Option B.

## Reason

- The project's own safety rule says demos never touch real files.
- The tool still runs real processes, so nothing in the demo is faked.
- The Stage 3 demo "agent tries a destructive command" still works: the gateway
  sees and blocks the request before it reaches the server. If a rule has a
  bug and the request gets through, the server refuses it and nothing is lost.

## Consequence

Commands with quoted arguments or shell syntax do not work on the sample
server. The gateway's rules must still be written for real commands, because a
real tool server in Stage 7 will not have this limit.
