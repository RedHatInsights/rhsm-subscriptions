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
package org.candlepin.subscriptions.validator;

import java.time.format.DateTimeFormatter;

/**
 * Enumeration for use with the Iso8061 validator annotation. Values correspond to DateTimeFormatter
 * instances.
 *
 * <p>Please note that some of these formats overlap with others. For example,
 * "2011-12-03T10:15:30+01:00" is both a valid ISO_DATE_TIME and a valid ISO_OFFSET_DATE_TIME. See
 * {@link DateTimeFormatter} for all the details.
 */
public enum Iso8601Format {
  ISO_DATE(DateTimeFormatter.ISO_DATE, "2011-12-03+01:00 or 2011-12-03"),
  ISO_LOCAL_DATE(DateTimeFormatter.ISO_LOCAL_DATE, "2011-12-03"),
  ISO_OFFSET_DATE(DateTimeFormatter.ISO_OFFSET_DATE, "2011-12-03+01:00"),

  ISO_TIME(DateTimeFormatter.ISO_TIME, "10:15:30+01:00 or 10:15:30"),
  ISO_LOCAL_TIME(DateTimeFormatter.ISO_LOCAL_TIME, "10:15:30"),
  ISO_OFFSET_TIME(DateTimeFormatter.ISO_OFFSET_TIME, "10:15:30+01:00"),

  ISO_DATE_TIME(
      DateTimeFormatter.ISO_DATE_TIME,
      "2011-12-03T10:15:30, 2011-12-03T10:15:30+01:00 or 2011-12-03T10:15:30+01:00[Europe/Paris]"),
  ISO_LOCAL_DATE_TIME(DateTimeFormatter.ISO_LOCAL_DATE_TIME, "2011-12-03T10:15:30"),
  ISO_OFFSET_DATE_TIME(DateTimeFormatter.ISO_OFFSET_DATE_TIME, "2011-12-03T10:15:30+01:00"),
  ISO_ZONED_DATE_TIME(
      DateTimeFormatter.ISO_ZONED_DATE_TIME, "2011-12-03T10:15:30+01:00[Europe/Paris]"),

  ISO_INSTANT(DateTimeFormatter.ISO_INSTANT, "2011-12-03T10:15:30Z");

  DateTimeFormatter formatter;

  /**
   * The predefined ISO 8601 formatters are not actually constructed using a String based pattern.
   * Even if they were, there's no way to get the pattern back once the formatter is constructed.
   * See <a href="https://stackoverflow.com/a/28949605/6124862">this StackOverflow answer</a>. As a
   * result, we need to provide at least some information to the user to show them what the right
   * format looks like. Rather than reverse engineer the string patterns for the formats (which I
   * would probably get wrong and would not necessarily be meaningful to the user), we will just
   * provide examples of what is in the correct format.
   */
  String example;

  Iso8601Format(DateTimeFormatter formatter, String example) {
    this.formatter = formatter;
    this.example = example;
  }
}
