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

import org.candlepin.subscriptions.clowder.DatabaseSslBeanPostProcessor;
import org.candlepin.subscriptions.clowder.KafkaJaasBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SystemConduitConfiguration {
  /**
   * A bean post-processor responsible for setting up JAAS for Kafka.
   *
   * @param env The Spring Environment
   * @return a KafkaJaasBeanPostProcessor object
   */
  @Bean
  public KafkaJaasBeanPostProcessor kafkaJaasBeanPostProcessor(Environment env) {
    return new KafkaJaasBeanPostProcessor(env);
  }

  /**
   * A bean post-processor responsible for setting up SSL for the database.
   *
   * @param env The Spring Environment
   * @return a databaseSslBeanPostProcessor object
   */
  @Bean
  public DatabaseSslBeanPostProcessor databaseSslBeanPostProcessor(Environment env) {
    return new DatabaseSslBeanPostProcessor(env);
  }
}
