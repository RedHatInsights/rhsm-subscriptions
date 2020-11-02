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
package org.candlepin.subscriptions.inventory.db;

import org.candlepin.subscriptions.db.PostgresTlsDataSourceProperties;
import org.candlepin.subscriptions.db.PostgresTlsHikariDataSourceFactoryBean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.annotation.Validated;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * A class to hold the inventory data source configuration.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.candlepin.subscriptions.inventory.db",
    entityManagerFactoryRef = "inventoryEntityManagerFactory")
public class InventoryDataSourceConfiguration {

    @Bean
    @Validated
    @ConfigurationProperties(prefix = "rhsm-subscriptions.inventory-service.datasource")
    public PostgresTlsDataSourceProperties inventoryDataSourceProperties() {
        return new PostgresTlsDataSourceProperties();
    }

    @Bean(name = "inventoryDataSource")
    public PostgresTlsHikariDataSourceFactoryBean inventoryDataSource(
        @Qualifier("inventoryDataSourceProperties") PostgresTlsDataSourceProperties dataSourceProperties) {
        PostgresTlsHikariDataSourceFactoryBean factory = new PostgresTlsHikariDataSourceFactoryBean();
        factory.setTlsDataSourceProperties(dataSourceProperties);
        return factory;
    }

    @Bean(name = "inventoryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean inventoryEntityManagerFactory(
        EntityManagerFactoryBuilder builder, @Qualifier("inventoryDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("org.candlepin.subscriptions.inventory.db.model")
                .persistenceUnit("inventory")
                .build();
    }

    @Bean(name = "inventoryTransactionManager")
    public PlatformTransactionManager inventoryTransactionManager(
        @Qualifier("inventoryEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
