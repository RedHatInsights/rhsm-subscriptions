/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
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
