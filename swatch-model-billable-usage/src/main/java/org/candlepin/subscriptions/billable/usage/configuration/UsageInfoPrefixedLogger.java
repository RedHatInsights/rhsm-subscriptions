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
package org.candlepin.subscriptions.billable.usage.configuration;

import org.candlepin.subscriptions.billable.usage.UsageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

public class UsageInfoPrefixedLogger {
  private final Logger logger;

  public UsageInfoPrefixedLogger(Class<?> clazz) {
    this.logger = LoggerFactory.getLogger(clazz);
  }

  public void info(UsageInfo prefix, String format, Object... arguments) {
    if (logger.isInfoEnabled()) {
      logger.info(formatMessage(prefix, format), arguments);
    }
  }

  public void debug(UsageInfo prefix, String format, Object... arguments) {
    if (logger.isDebugEnabled()) {
      logger.debug(formatMessage(prefix, format), arguments);
    }
  }

  public void warn(UsageInfo prefix, String format, Object... arguments) {
    if (logger.isWarnEnabled()) {
      logger.warn(formatMessage(prefix, format), arguments);
    }
  }

  public void error(UsageInfo prefix, String format, Object... arguments) {
    if (logger.isErrorEnabled()) {
      logger.error(formatMessage(prefix, format), arguments);
    }
  }

  private String formatMessage(UsageInfo prefix, String format) {
    return MessageFormatter.arrayFormat("{} " + format, new Object[] {prefix}).getMessage();
  }
}
