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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import javax.validation.Validator;
import org.candlepin.subscriptions.capacity.CapacityIngressConfiguration;
import org.candlepin.subscriptions.conduit.ConduitConfiguration;
import org.candlepin.subscriptions.conduit.job.OrgSyncConfiguration;
import org.candlepin.subscriptions.files.ProductMappingConfiguration;
import org.candlepin.subscriptions.marketplace.MarketplaceWorkerConfiguration;
import org.candlepin.subscriptions.metering.MeteringConfiguration;
import org.candlepin.subscriptions.resource.ApiConfiguration;
import org.candlepin.subscriptions.retention.PurgeSnapshotsConfiguration;
import org.candlepin.subscriptions.security.SecurityConfig;
import org.candlepin.subscriptions.subscription.SubscriptionServiceConfiguration;
import org.candlepin.subscriptions.tally.TallyWorkerConfiguration;
import org.candlepin.subscriptions.tally.job.CaptureHourlySnapshotsConfiguration;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsConfiguration;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.user.UserServiceClientConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.HawtioConfiguration;
import org.candlepin.subscriptions.util.LiquibaseUpdateOnlyConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Class to hold configuration beans common to all profiles and import all profile configurations
 */
@Configuration
@Import({
  ApiConfiguration.class,
  ConduitConfiguration.class,
  CapacityIngressConfiguration.class,
  CaptureSnapshotsConfiguration.class,
  CaptureHourlySnapshotsConfiguration.class,
  PurgeSnapshotsConfiguration.class,
  LiquibaseUpdateOnlyConfiguration.class,
  TallyWorkerConfiguration.class,
  OrgSyncConfiguration.class,
  MarketplaceWorkerConfiguration.class,
  ProductMappingConfiguration.class,
  DevModeConfiguration.class,
  SecurityConfig.class,
  HawtioConfiguration.class,
  MeteringConfiguration.class,
  SubscriptionServiceConfiguration.class,
  UserServiceClientConfiguration.class
})
public class ApplicationConfiguration implements WebMvcConfigurer {
  @Bean
  ApplicationProperties applicationProperties() {
    return new ApplicationProperties();
  }

  @Bean
  @Qualifier("marketplaceTasks")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.marketplace-tasks")
  TaskQueueProperties tallySummaryQueueProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Qualifier("subscriptionTasks")
  @ConfigurationProperties(prefix = "rhsm-subscriptions.subscription-tasks")
  TaskQueueProperties syncSubscriptionQueueProperties() {
    return new TaskQueueProperties();
  }

  @Bean
  @Primary
  ObjectMapper objectMapper(ApplicationProperties applicationProperties) {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    objectMapper.configure(
        SerializationFeature.INDENT_OUTPUT, applicationProperties.isPrettyPrintJson());
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());

    // Explicitly load the modules we need rather than use ObjectMapper.findAndRegisterModules in
    // order to
    // avoid com.fasterxml.jackson.module.scala.DefaultScalaModule, which was causing
    // deserialization
    // to ignore @JsonProperty on OpenApi classes.
    objectMapper.registerModule(new JaxbAnnotationModule());
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new Jdk8Module());

    return objectMapper;
  }

  @Bean
  ApplicationClock applicationClock() {
    return new ApplicationClock();
  }

  /* Do not declare a MethodValidationPostProcessor!
   *
   * The Spring Core documents instruct the user to create a MethodValidationPostProcessor in order to
   * enable method validation.  However, Spring Boot takes care of creating that bean that itself:
   * "The method validation feature supported by Bean Validation 1.1 is automatically enabled as long as a
   * JSR-303 implementation (such as Hibernate validator) is on the classpath" (from
   * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-validation).
   *
   * Creating our own MethodValidationPostProcessor causes ConstraintValidator implementations to *not*
   * receive injection from the Spring IoC container.
   */

  @Bean
  public Validator validator() {
    return new LocalValidatorFactoryBean();
  }

  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }
}
