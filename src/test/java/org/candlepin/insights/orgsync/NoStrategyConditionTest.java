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
package org.candlepin.insights.orgsync;

import static org.candlepin.insights.orgsync.OrgSyncConfiguration.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.List;

public class NoStrategyConditionTest {
    private ConfigurableApplicationContext context;
    private ConfigurableEnvironment environment = new StandardEnvironment();

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * The bean in this class should be loaded if the NoStrategyCondition is fulfilled.
     * And the NoStrategyCondition is fulfilled when something is *wrong*.  In practical terms,
     * this means that the conditions in the above tests can seem backwards at first glance.
     */
    @Configuration
    protected static class ConditionalConfiguration {
        @Bean
        @Conditional(NoStrategyCondition.class)
        public String foo() {
            return "foo";
        }
    }

    @Configuration
    protected static class BaseConfiguration {
        @Bean
        public String badBean() {
            return "I am a bean of the wrong class";
        }

        @Bean
        public OrgListStrategy goodBean() {
            return new OrgListStrategy() {
                @Override
                public List<String> getOrgsToSync() throws IOException {
                    return null;
                }
            };
        }
    }

    @Test
    public void testStrategyPropertySet() {
        load(BaseConfiguration.class, ORG_LIST_STRATEGY + "=goodBean");
        assertFalse(context.containsBean("foo"));
    }

    @Test
    public void testStrategyPropertyMissing() {
        load(BaseConfiguration.class, "bar=baz");
        assertTrue(context.containsBean("foo"));
    }

    @Test
    public void testStrategyPropertyWrong() {
        load(BaseConfiguration.class, ORG_LIST_STRATEGY + "=missingBean");
        assertTrue(context.containsBean("foo"));
    }

    @Test
    public void testStrategyPropertyWrongClass() {
        load(BaseConfiguration.class, ORG_LIST_STRATEGY + "=badBean");
        assertTrue(context.containsBean("foo"));
    }

    private void load(Class<?> config, String... environmentValues) {
        TestPropertyValues.of(environmentValues).applyTo(environment);
        context = new SpringApplicationBuilder(config, ConditionalConfiguration.class)
            .environment(environment)
            .web(WebApplicationType.NONE)
            .run();
    }
}
