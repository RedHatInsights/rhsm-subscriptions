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
package org.candlepin.subscriptions.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

/**
 * HttpMessageConverter for java.io.File objects. Converts File objects to/from byte streams for
 * HTTP requests/responses.
 */
public class FileHttpMessageConverter extends AbstractHttpMessageConverter<File> {

  public FileHttpMessageConverter() {
    super(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL);
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return File.class.isAssignableFrom(clazz);
  }

  @Override
  protected File readInternal(Class<? extends File> clazz, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    throw new UnsupportedOperationException("Reading File from HTTP response not supported");
  }

  @Override
  protected void writeInternal(File file, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    try (FileInputStream fis = new FileInputStream(file)) {
      StreamUtils.copy(fis, outputMessage.getBody());
    }
  }
}
