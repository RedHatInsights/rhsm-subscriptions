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

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.candlepin.subscriptions.actuator.CertInfoContributor;
import org.candlepin.subscriptions.clowder.KafkaSslBeanPostProcessor;
import org.candlepin.subscriptions.clowder.RdsSslBeanPostProcessor;
import org.candlepin.subscriptions.configuration.UnleashConfiguration;
import org.candlepin.subscriptions.db.RhsmSubscriptionsDataSourceConfiguration;
import org.candlepin.subscriptions.resource.ApiConfiguration;
import org.candlepin.subscriptions.security.AuthProperties;
import org.candlepin.subscriptions.security.SecurityConfiguration;
import org.candlepin.subscriptions.tally.TallyWorkerConfiguration;
import org.candlepin.subscriptions.util.LiquibaseUpdateOnlyConfiguration;
import org.candlepin.subscriptions.util.UtilConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorFallback;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.StdDateFormat;

/**
 * Class to hold configuration beans common to all profiles and import all profile configurations
 */
@Configuration
@Import({
  ApiConfiguration.class,
  TallyWorkerConfiguration.class,
  DevModeConfiguration.class,
  SecurityConfiguration.class,
  RhsmSubscriptionsDataSourceConfiguration.class,
  LiquibaseUpdateOnlyConfiguration.class,
  UtilConfiguration.class,
  UnleashConfiguration.class,
})
public class ApplicationConfiguration implements WebMvcConfigurer {
  @Bean
  ApplicationProperties applicationProperties() {
    return new ApplicationProperties();
  }

  @Bean
  AuthProperties authProperties() {
    return new AuthProperties();
  }

  @Bean(name = {"objectMapper", "jacksonObjectMapper", "jacksonJsonMapper"})
  @Primary
  JsonMapper objectMapper(ApplicationProperties applicationProperties) {
    // Jackson 3: Use builder pattern (JsonMapper is immutable)
    // Note: Jackson 3 includes JavaTime and JDK8 modules by default, no need to register them
    // Return type is JsonMapper (not ObjectMapper) for Spring Kafka JacksonJson* serializers
    return JsonMapper.builder()
        .defaultDateFormat(new StdDateFormat().withColonInTimeZone(true))
        .configure(SerializationFeature.INDENT_OUTPUT, applicationProperties.isPrettyPrintJson())
        .changeDefaultPropertyInclusion(
            include ->
                include.withValueInclusion(
                    com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .annotationIntrospector(new JacksonAnnotationIntrospector())
        .build();
  }

  /* Do not declare a MethodValidationPostProcessor!
   *
   * The Spring Core documents instruct the user to create a MethodValidationPostProcessor in order to
   * enable method validation.  However, Spring Boot takes care of creating that bean itself:
   * "The method validation feature supported by Bean Validation 1.1 is automatically enabled as long as a
   * JSR-303 implementation (such as Hibernate validator) is on the classpath" (from
   * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-validation).
   *
   * Creating our own MethodValidationPostProcessor causes ConstraintValidator implementations to *not*
   * receive injection from the Spring IoC container.
   */

  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }

  /**
   * A bean post-processor responsible for setting up Kafka truststores correctly. It's declared
   * here so that this bean will always be created. In other words, the creation of this bean isn't
   * dependent on the web of Import annotations that we have declared across our Configuration
   * classes. ApplicationConfiguration is the one Configuration class we can always count on to
   * load.
   *
   * @return a KafkaJaasBeanPostProcessor object
   */
  @Bean
  public static KafkaSslBeanPostProcessor kafkaJaasBeanPostProcessor() {
    return new KafkaSslBeanPostProcessor();
  }

  @Bean
  public static RdsSslBeanPostProcessor rdsSslBeanPostProcessor(Environment env) {
    return new RdsSslBeanPostProcessor(env);
  }

  @Bean
  @ConditionalOnEnabledInfoContributor(value = "certs", fallback = InfoContributorFallback.DISABLE)
  public CertInfoContributor certInfoContributor(ApplicationContext context) {
    return new CertInfoContributor(context);
  }
}
