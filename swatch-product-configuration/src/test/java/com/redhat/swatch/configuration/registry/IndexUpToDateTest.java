package com.redhat.swatch.configuration.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class IndexUpToDateTest {

  @Test
  void testIndexUpToDate() throws IOException {
    try (var index = getClass().getClassLoader().getResourceAsStream("swatch_config_index.txt")) {
      assertNotNull(index);
      var lines = Arrays.stream(
              new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
          .filter(s -> !s.isEmpty()).toList();
      try (var files = Files.walk(Path.of("src", "main", "resources", "subscription_configs"))) {
        var allFiles = files.filter(f -> f.toFile().isFile())
            .map(p -> Path.of("src", "main", "resources").relativize(p).toString())
            .sorted()
            .toList();
        assertEquals(lines, allFiles);
      }
    }
  }
}
