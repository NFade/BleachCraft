package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Fails if any source under core/ imports net.minecraft or net.fabricmc (ADR section 6, R6.2). */
class CorePurityTest {
	@Test
	void coreHasNoMinecraftOrFabricImports() throws IOException {
		Path core = Paths.get("src", "main", "java", "dev", "minebleach", "reiatsutest", "core");
		assertTrue(Files.isDirectory(core), "run from the mod/ project dir: " + core.toAbsolutePath());
		try (Stream<Path> files = Files.walk(core)) {
			List<Path> java = files.filter(p -> p.toString().endsWith(".java")).toList();
			assertFalse(java.isEmpty());
			for (Path p : java) {
				for (String line : Files.readAllLines(p)) {
					String t = line.trim();
					boolean banned = t.contains("net.minecraft") || t.contains("net.fabricmc");
					assertFalse(banned && !t.startsWith("//") && !t.startsWith("*"), p + " imports a banned package: " + t);
				}
			}
		}
	}
}
