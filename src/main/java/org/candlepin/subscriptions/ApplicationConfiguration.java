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
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import org.candlepin.subscriptions.actuator.CertInfoContributor;
import org.candlepin.subscriptions.capacity.CapacityIngressConfiguration;
import org.candlepin.subscriptions.clowder.KafkaSslBeanPostProcessor;
import org.candlepin.subscriptions.clowder.RdsSslBeanPostProcessor;
import org.candlepin.subscriptions.db.RhsmSubscriptionsDataSourceConfiguration;
import org.candlepin.subscriptions.resource.ApiConfiguration;
import org.candlepin.subscriptions.rhmarketplace.RhMarketplaceWorkerConfiguration;
import org.candlepin.subscriptions.security.AuthProperties;
import org.candlepin.subscriptions.security.SecurityConfiguration;
import org.candlepin.subscriptions.subscription.SubscriptionServiceConfiguration;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Class to hold configuration beans common to all profiles and import all profile configurations
 */
@Configuration
@Import({
  ApiConfiguration.class,
  CapacityIngressConfiguration.class,
  LiquibaseUpdateOnlyConfiguration.class,
  TallyWorkerConfiguration.class,
  RhMarketplaceWorkerConfiguration.class,
  DevModeConfiguration.class,
  SecurityConfiguration.class,
  SubscriptionServiceConfiguration.class,
  // NOTE(khowell): actually not needed in RH marketplace worker
  RhsmSubscriptionsDataSourceConfiguration.class,
  UtilConfiguration.class,
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
    // order to avoid com.fasterxml.jackson.module.scala.DefaultScalaModule, which was causing
    // deserialization to ignore @JsonProperty on OpenApi classes.
    objectMapper.registerModule(new JakartaXmlBindAnnotationModule());
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new Jdk8Module());

    return objectMapper;
  }

  @Bean
  CsvMapper csvMapper() {
    CsvMapper csvMapper = new CsvMapper();
    csvMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    csvMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    csvMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    csvMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    csvMapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
    // Explicitly load the modules we need rather than use ObjectMapper.findAndRegisterModules in
    // order to avoid com.fasterxml.jackson.module.scala.DefaultScalaModule, which was causing
    // deserialization to ignore @JsonProperty on OpenApi classes.
    csvMapper.registerModule(new JakartaXmlBindAnnotationModule());
    csvMapper.registerModule(new JavaTimeModule());
    csvMapper.registerModule(new Jdk8Module());
    return csvMapper;
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
  public Validator validator() {
    return new LocalValidatorFactoryBean();
  }

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
  public KafkaSslBeanPostProcessor kafkaJaasBeanPostProcessor() {
    return new KafkaSslBeanPostProcessor();
  }

  @Bean
  public RdsSslBeanPostProcessor rdsSslBeanPostProcessor(Environment env) {
    return new RdsSslBeanPostProcessor(env);
  }

  @Bean
  @ConditionalOnEnabledInfoContributor(value = "certs", fallback = InfoContributorFallback.DISABLE)
  public CertInfoContributor certInfoContributor(ApplicationContext context) {
    return new CertInfoContributor(context);
  }
}
