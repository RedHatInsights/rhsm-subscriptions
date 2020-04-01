/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

package org.candlepin.insights.orgsync.db;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

/**
 * Class to create the HikariDataSource.  Highly PostgreSQL specific.  Other databases will require their
 * own FactoryBean.
 */
public class PostgresTlsHikariDataSourceFactoryBean extends AbstractFactoryBean<HikariDataSource>
    implements ResourceLoaderAware {
    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    private PostgresTlsDataSourceProperties tlsProperties;

    @Override
    public Class<?> getObjectType() {
        return HikariDataSource.class;
    }

    @Override
    protected HikariDataSource createInstance() throws Exception {
        DataSourceBuilder<HikariDataSource> builder =
            tlsProperties.initializeDataSourceBuilder().type(HikariDataSource.class);

        HikariDataSource dataSource = builder.build();
        // If you set "ssl" to any value at all, the JDBC driver will try to connect over TLS, so do not add
        // any properties at all if 'enable-tls' isn't true.
        if ("postgresql".equalsIgnoreCase(tlsProperties.getPlatform()) &&
            tlsProperties.isEnableTls()) {
            dataSource.addDataSourceProperty("ssl", tlsProperties.isEnableTls());
            dataSource.addDataSourceProperty("sslmode", tlsProperties.getVerificationMode());
            dataSource.addDataSourceProperty("sslrootcert",
                getRootCaFile(tlsProperties.getRootCaLocation()));
        }

        return dataSource;
    }

    /**
     * Method to return the canonical path to the CA file.  Any overriding implementation should fail fast on
     * a read error by throwing an exception; it should not return null/empty string as that will only obscure
     * the actual issue.
     *
     * @param rootCaLocation the Spring resource string representing the location of the root CA.  E.g.
     * "classpath:", "file:", etc. prefixes are accepted.
     * @return the path on the file-system to the resource.
     */
    protected String getRootCaFile(String rootCaLocation) {
        Resource ca = resourceLoader.getResource(rootCaLocation);
        try {
            return ca.getFile().getCanonicalPath();
        }
        catch (IOException e) {
            // If we can't read the CA, just die right now.  Any further error messages will only obscure the
            // true problem
            throw new BeanInitializationException("Could not read root CA at " + rootCaLocation, e);
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setTlsDataSourceProperties(PostgresTlsDataSourceProperties postgresTlsDataSourceProperties) {
        this.tlsProperties = postgresTlsDataSourceProperties;
    }
}
