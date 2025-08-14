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
package com.redhat.swatch.component.tests.logging;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

public class FileServiceLoggingHandler extends ServiceLoggingHandler {

  private final File file;
  private String printedContent;

  public FileServiceLoggingHandler(Service context, File input) {
    super(context);
    this.file = input;
  }

  @Override
  protected synchronized void handle() {
    if (file.exists()) {
      String newContent = FileUtils.loadFile(file);
      onStringDifference(newContent, printedContent);
      printedContent = newContent;
    }
  }

  @Override
  public List<String> logs() {
    try {
      return Files.readAllLines(file.toPath(), Charset.defaultCharset());
    } catch (IOException e) {
      Log.warn("Exception reading file log file", e);
      // Fallback to default implementation:
      return super.logs();
    }
  }
}
