/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Filter that is used to show only com.j256.cloudwatchlogbackappender.CloudWatchAppender messages.
 *
 * CloudWatchAppender writes *all* messages to its emergency appender if it encounters a problem, but
 * we only want to see messages that tell us that CloudWatchAppender is not functioning, as we'll write
 * logs to ConsoleAppender already.
 *
 * This filter allows us to write all log messages to both CloudWatch and the console, without unnecessary
 * repetitions.
 */
public class CloudWatchEmergencyFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getLoggerName().equals("com.j256.cloudwatchlogbackappender.CloudWatchAppender")) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
    }
}
