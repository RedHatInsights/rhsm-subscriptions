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
package org.candlepin.subscriptions.test;

import org.candlepin.testcontainers.SwatchPostgreSQLContainer;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
public interface ExtendWithSwatchDatabase {

  @Container
  SwatchPostgreSQLContainer swatchDatabase = new SwatchPostgreSQLContainer("rhsm-subscriptions");

  @DynamicPropertySource
  static void registerSwatchDbProperties(DynamicPropertyRegistry registry) {
    registry.add("DATABASE_HOST", swatchDatabase::getHost);
    registry.add("DATABASE_PORT", swatchDatabase::getFirstMappedPort);
    // Also register spring.datasource.url for Liquibase
    registry.add("spring.datasource.url", swatchDatabase::getJdbcUrl);
    registry.add("spring.datasource.username", swatchDatabase::getUsername);
    registry.add("spring.datasource.password", swatchDatabase::getPassword);
    registry.add("spring.liquibase.enabled", () -> "true");
    registry.add("spring.liquibase.change-log", () -> "classpath:/liquibase/changelog.xml");
    registry.add("spring.liquibase.show-summary", () -> "summary");
  }
}
