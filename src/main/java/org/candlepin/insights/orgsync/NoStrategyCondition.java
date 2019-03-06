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
