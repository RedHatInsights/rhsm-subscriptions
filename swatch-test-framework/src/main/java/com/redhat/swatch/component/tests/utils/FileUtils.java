/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.component.tests.utils;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;

public final class FileUtils {

  private static final int NO_RECURSIVE = 1;

  private FileUtils() {}

  public static Path copyContentTo(String content, Path target) {
    try {
      org.apache.commons.io.FileUtils.createParentDirectories(target.toFile());
      Files.writeString(target, content, CREATE, APPEND);
    } catch (IOException e) {
      throw new RuntimeException("Failed when writing file " + target, e);
    }

    return target;
  }

  public static void copyFileTo(File file, Path target) {
    try {
      org.apache.commons.io.FileUtils.copyFileToDirectory(file, target.toFile());
    } catch (IOException e) {
      throw new RuntimeException("Could not copy project.", e);
    }
  }

  public static void copyDirectoryTo(Path source, Path target) {
    if (!source.toFile().exists()) {
      return;
    }

    try {
      org.apache.commons.io.FileUtils.copyDirectory(source.toFile(), target.toFile());
    } catch (IOException e) {
      throw new RuntimeException("Could not copy source: " + source, e);
    }
  }

  public static String loadFile(File file) {
    try {
      return org.apache.commons.io.FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Could not load file " + file, e);
    }
  }

  public static String loadFile(String location) {
    try {
      return IOUtils.toString(
          FileUtils.class.getResourceAsStream(location), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Could not load file " + location, e);
    }
  }

  public static void recreateDirectory(Path folder) {
    deletePath(folder);
    createDirectory(folder);
  }

  public static void createDirectory(Path folder) {
    try {
      org.apache.commons.io.FileUtils.forceMkdir(folder.toFile());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void createDirectoryIfDoesNotExist(Path folder) {
    if (!Files.exists(folder)) {
      folder.toFile().mkdirs();
    }
  }

  public static void deletePath(Path folder) {
    deleteFile(folder.toFile());
  }

  public static void deleteFile(File file) {
    if (file.exists()) {
      try {
        org.apache.commons.io.FileUtils.forceDelete(file);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Optional<String> findFile(Path basePath, String endsWith) {
    return findFile(basePath, f -> f.endsWith(endsWith));
  }

  public static Optional<String> findFile(Path basePath, Predicate<String> filter) {
    try (Stream<Path> binariesFound =
        Files.find(
            basePath,
            NO_RECURSIVE,
            (path, basicFileAttributes) -> filter.test(path.toFile().getName()))) {
      List<String> found =
          binariesFound.map(path -> path.normalize().toString()).collect(Collectors.toList());
      if (found.size() == 1) {
        return Optional.of(found.get(0));
      }
    } catch (IOException ex) {
      // ignored
    }

    return Optional.empty();
  }
}
