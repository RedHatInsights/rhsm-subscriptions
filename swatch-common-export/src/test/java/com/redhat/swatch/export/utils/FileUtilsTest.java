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
package com.redhat.swatch.export.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import org.junit.jupiter.api.Test;

class FileUtilsTest {
  @Test
  void testGetFileSizeInBytes() {
    String output = FileUtils.getFileSize(new FileWithFixedSize(500));
    assertEquals("500 b", output);
  }

  @Test
  void testGetFileSizeInKiloBytes() {
    String output = FileUtils.getFileSize(new FileWithFixedSize(1500));
    assertEquals("1.46 kb", output);
  }

  @Test
  void testGetFileSizeInMegaBytes() {
    String output = FileUtils.getFileSize(new FileWithFixedSize(2500000));
    assertEquals("2.38 mb", output);
  }

  private static class FileWithFixedSize extends File {
    private final long fileSize;

    public FileWithFixedSize(long fileSize) {
      super("");
      this.fileSize = fileSize;
    }

    @Override
    public long length() {
      return fileSize;
    }
  }
}
