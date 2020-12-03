/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.db;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.tally.files.FileAccountSyncListSource;
import org.candlepin.subscriptions.tally.files.ReportingAccountWhitelist;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * A class to hold the inventory data source configuration.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.candlepin.subscriptions.db",
    entityManagerFactoryRef = "rhsmSubscriptionsEntityManagerFactory",
    transactionManagerRef = "rhsmSubscriptionsTransactionManager")
public class RhsmSubscriptionsDataSourceConfiguration {

    @Bean
    @Validated
    @Primary
    @ConfigurationProperties("rhsm-subscriptions.datasource")
    public PostgresTlsDataSourceProperties rhsmDataSourceProperties() {
        return new PostgresTlsDataSourceProperties();
    }

    @Bean(name = "rhsmSubscriptionsDataSource")
    @Primary
    public PostgresTlsHikariDataSourceFactoryBean rhsmSubscriptionsDataSource(
        @Qualifier("rhsmDataSourceProperties") PostgresTlsDataSourceProperties rhsmDataSourceProperties) {

        PostgresTlsHikariDataSourceFactoryBean factory = new PostgresTlsHikariDataSourceFactoryBean();
        factory.setTlsDataSourceProperties(rhsmDataSourceProperties);
        return factory;
    }

    @Bean(name = "rhsmSubscriptionsEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean rhsmSubscriptionsEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("rhsmSubscriptionsDataSource") DataSource dataSource) {
        return builder
                   .dataSource(dataSource)
                   .packages("org.candlepin.subscriptions.db.model")
                   .persistenceUnit("rhsm-subscriptions")
                   .build();
    }

    @Bean(name = "rhsmSubscriptionsTransactionManager")
    @Primary
    public PlatformTransactionManager rhsmSubscriptionsTransactionManager(
        @Qualifier("rhsmSubscriptionsEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public AccountListSource accountListSource(ApplicationProperties applicationProperties,
        AccountConfigRepository accountConfigRepository, ApplicationClock clock) {

        if (StringUtils.hasText(applicationProperties.getAccountListResourceLocation())) {
            return new FileAccountListSource(
                    new FileAccountSyncListSource(applicationProperties, clock),
                    new ReportingAccountWhitelist(applicationProperties, clock)
            );
        }
        else {
            return new DatabaseAccountListSource(accountConfigRepository);
        }
    }
}
