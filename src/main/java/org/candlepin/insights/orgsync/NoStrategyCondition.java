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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * A Condition that is meant to be true when an acceptable value for the OrgListStrategy is absent.
 * The bean with this condition applies should just throw an exception to immediately terminate the
 * application since the settings for the application are invalid.
 *
 * TODO: This tactic of having a fail-safe bean that throws an exception feels like a hack
 */
public class NoStrategyCondition extends SpringBootCondition {
    private static final Logger log = LoggerFactory.getLogger(NoStrategyCondition.class);

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        PropertyResolver resolver = context.getEnvironment();
        if (resolver.containsProperty(OrgSyncConfiguration.ORG_LIST_STRATEGY)) {
            String value = resolver.getProperty(OrgSyncConfiguration.ORG_LIST_STRATEGY);
            log.debug("Searching for strategy {} for OrgSyncJob", value);

            BeanFactory beanFactory = context.getBeanFactory();
            if (beanFactory.containsBean(value) && beanFactory.isTypeMatch(value, OrgListStrategy.class)) {
                return ConditionOutcome.noMatch(ConditionMessage
                    .forCondition(NoStrategyCondition.class.getSimpleName())
                    .found("valid").items(OrgSyncConfiguration.ORG_LIST_STRATEGY));
            }
            else {
                return ConditionOutcome.match(ConditionMessage
                    .forCondition(NoStrategyCondition.class.getSimpleName())
                    .didNotFind("valid").items(OrgSyncConfiguration.ORG_LIST_STRATEGY));
            }
        }
        else {
            return ConditionOutcome.match(ConditionMessage
                .forCondition(NoStrategyCondition.class.getSimpleName())
                .didNotFind("property").items(OrgSyncConfiguration.ORG_LIST_STRATEGY));
        }
    }
}
