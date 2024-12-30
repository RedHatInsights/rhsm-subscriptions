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

import java.io.File;
import java.text.DecimalFormat;

public final class FileUtils {

  private static final DecimalFormat FORMAT = new DecimalFormat("##.00");
  private static final double BYTE = 1024;

  private FileUtils() {}

  public static String getFileSize(File file) {
    double fileSize = file.length();
    if (fileSize < BYTE) {
      return (int) fileSize + " b";
    }

    fileSize = fileSize / BYTE;
    if (fileSize < BYTE) {
      return FORMAT.format(fileSize) + " kb";
    }

    return FORMAT.format(fileSize / BYTE) + " mb";
  }
}
