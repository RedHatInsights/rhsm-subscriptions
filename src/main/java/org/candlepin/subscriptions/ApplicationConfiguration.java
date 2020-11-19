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
package org.candlepin.subscriptions;

import org.candlepin.subscriptions.capacity.CapacityIngressConfiguration;
import org.candlepin.subscriptions.resource.ApiConfiguration;
import org.candlepin.subscriptions.retention.PurgeSnapshotsConfiguration;
import org.candlepin.subscriptions.security.SecurityConfig;
import org.candlepin.subscriptions.tally.TallyWorkerConfiguration;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.HawtioConfiguration;
import org.candlepin.subscriptions.util.LiquibaseUpdateOnlyConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

import javax.validation.Validator;

/** Class to hold configuration beans common to all profiles and import all profile configurations */
@Configuration
@Import({
    ApiConfiguration.class, CapacityIngressConfiguration.class, CaptureSnapshotsConfiguration.class,
    PurgeSnapshotsConfiguration.class, LiquibaseUpdateOnlyConfiguration.class, TallyWorkerConfiguration.class,
    DevModeConfiguration.class, SecurityConfig.class, HawtioConfiguration.class
})
public class ApplicationConfiguration implements WebMvcConfigurer {
    @Bean
    ApplicationProperties applicationProperties() {
        return new ApplicationProperties();
    }

    @Bean
    ApplicationClock applicationClock() {
        return new ApplicationClock();
    }

    /**
     * Tell Spring AOP to run methods in classes marked @Validated through the JSR-303 Validation
     * implementation.  Validations that fail will throw an ConstraintViolationException.
     * @return post-processor used by Spring AOP
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }

    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
