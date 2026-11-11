package dev.ahmedmohsin.blackbox.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * The one folder the sample server is allowed to touch.
 *
 * <p>Every path a tool receives goes through {@link #resolve}. The sample server must be safe on
 * its own, without the gateway in front of it, because Stage 0 runs it directly.
 */
final class Sandbox {

  /** Thrown when a path would leave the sandbox. The message is shown to the agent. */
  static final class SandboxException extends Exception {
    SandboxException(String message) {
      super(message);
    }
  }

  private final Path root;

  Sandbox(Path root) throws IOException {
    // The real path, so later comparisons are not fooled by a symlinked temp folder
    // (on macOS /var is itself a symlink to /private/var).
    this.root = root.toRealPath();
  }

  Path root() {
    return root;
  }

  /**
   * Turns a path given by the agent into a path inside the sandbox, or refuses.
   *
   * <p>Three separate ways out are closed: absolute paths, ".." segments, and symlinks that point
   * outside.
   */
  Path resolve(String given) throws SandboxException {
    if (given == null || given.isBlank()) {
      throw new SandboxException("path is missing or empty");
    }
    Path relative;
    try {
      relative = Path.of(given);
    } catch (InvalidPathException e) {
      throw new SandboxException("not a valid path: " + given);
    }
    if (relative.isAbsolute()) {
      throw new SandboxException("absolute paths are not allowed: " + given);
    }

    // normalize() removes "." and ".." without touching the disk, so "a/../../b" is caught here.
    Path candidate = root.resolve(relative).normalize();
    if (!candidate.startsWith(root) || candidate.equals(root)) {
      throw new SandboxException("path is outside the sandbox: " + given);
    }

    // normalize() knows nothing about symlinks. Follow the deepest part of the path that already
    // exists and check where it really leads.
    Path existing = candidate;
    while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
      existing = existing.getParent();
    }
    try {
      if (!existing.toRealPath().startsWith(root)) {
        throw new SandboxException("path leads outside the sandbox through a link: " + given);
      }
    } catch (IOException e) {
      // For example a link whose target is missing. If we cannot tell where it leads, refuse.
      throw new SandboxException("path cannot be checked: " + given);
    }
    return candidate;
  }
}
