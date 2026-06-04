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
package org.candlepin.subscriptions;

import java.time.ZoneOffset;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/** Bootstrapper for Spring Boot. */
@SpringBootConfiguration
@EnableAspectJAutoProxy
@EnableAutoConfiguration
@EnableConfigurationProperties
@Import(ApplicationConfiguration.class)
@SuppressWarnings("checkstyle:hideutilityclassconstructor")
public class BootApplication {
  public static void main(String[] args) {
    /*
    Force the JVM to operate in UTC, see org.candlepin.subscriptions.util.ApplicationClock

    Hibernate will return OffsetDateTime in the system timezone, while we coerce dates into UTC in
    ApplicationClock! Setting it here means the whole application deals exclusively with UTC.
     */
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    // Force liquibase-hub to off to avoid unnecessary warnings in our logs
    System.setProperty("liquibase.hub.mode", "off");
    // To remove the file size limitation that resteasy client has by default which is 50MB.
    // More information in https://github.com/orgs/resteasy/discussions/4085.
    // Note that this needs to be set via system properties since using the client builder
    // is not supported.
    System.setProperty("dev.resteasy.entity.file.threshold", "-1");

    SpringApplication app = new SpringApplication(BootApplication.class);
    app.run(args);
  }
}
