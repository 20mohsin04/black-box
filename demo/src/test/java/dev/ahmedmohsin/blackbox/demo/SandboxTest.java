package dev.ahmedmohsin.blackbox.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ahmedmohsin.blackbox.demo.Sandbox.SandboxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SandboxTest {

  @TempDir Path temp;
  private Path outside;
  private Sandbox sandbox;

  @BeforeEach
  void setUp() throws IOException {
    outside = Files.createDirectory(temp.resolve("outside"));
    sandbox = new Sandbox(Files.createDirectory(temp.resolve("sandbox")));
  }

  @Test
  void allowsPathsInside() throws Exception {
    assertEquals(sandbox.root().resolve("a/b.txt"), sandbox.resolve("a/b.txt"));
    assertEquals(sandbox.root().resolve("b.txt"), sandbox.resolve("a/../b.txt"));
  }

  @Test
  void refusesAbsolutePaths() {
    assertThrows(SandboxException.class, () -> sandbox.resolve("/etc/passwd"));
  }

  @Test
  void refusesParentSegments() {
    assertThrows(SandboxException.class, () -> sandbox.resolve("../outside/x.txt"));
    assertThrows(SandboxException.class, () -> sandbox.resolve("a/../../outside/x.txt"));
  }

  @Test
  void refusesTheSandboxFolderItselfAndEmptyPaths() {
    assertThrows(SandboxException.class, () -> sandbox.resolve("."));
    assertThrows(SandboxException.class, () -> sandbox.resolve(" "));
    assertThrows(SandboxException.class, () -> sandbox.resolve(null));
  }

  @Test
  void refusesLinksThatLeadOutside() throws IOException {
    Files.createSymbolicLink(sandbox.root().resolve("link"), outside);
    // Both the link itself and a new file "under" it would land outside.
    assertThrows(SandboxException.class, () -> sandbox.resolve("link"));
    assertThrows(SandboxException.class, () -> sandbox.resolve("link/new.txt"));
  }

  @Test
  void refusesLinksWhoseTargetIsMissing() throws IOException {
    Files.createSymbolicLink(sandbox.root().resolve("dangling"), temp.resolve("not-there"));
    assertThrows(SandboxException.class, () -> sandbox.resolve("dangling"));
  }
}
