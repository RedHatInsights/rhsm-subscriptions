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
package com.redhat.swatch.contract.config;

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
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.OidcTokenProvider;
import com.redhat.swatch.clients.TokenProvider;
import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.common.security.KesselProperties;
import com.redhat.swatch.contract.service.export.SubscriptionDataExporterService;
import com.redhat.swatch.export.CsvExportFileWriter;
import com.redhat.swatch.export.ExportRequestHandler;
import com.redhat.swatch.export.JsonExportFileWriter;
import com.redhat.swatch.export.api.ExportDelegate;
import com.redhat.swatch.export.api.ExportDelegateImpl;
import com.redhat.swatch.export.api.RbacDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Quarkus wiring for the shared export-service client ({@link SwatchExportApi}). Logic lives in
 * {@code base-client} / {@code export-client}; this class only binds Clowder/OIDC config.
 */
@ApplicationScoped
public class ExportClientConfiguration {

  public static final String EXPORT_SERVICE = "export-service";

  @ConfigProperty(name = "SWATCH_EXPORT_PSK", defaultValue = "placeholder")
  String psk;

  @ConfigProperty(name = "EXPORT_SERVICE_READ_TIMEOUT", defaultValue = "120s")
  Duration readTimeout;

  @ConfigProperty(name = "EXPORT_SERVICE_ENDPOINT")
  Optional<String> uri;

  @ConfigProperty(name = "EXPORT_SERVICE_AUTHENTICATED")
  Optional<String> authenticated;

  @ConfigProperty(name = "EXPORT_SERVICE_TRUST_STORE_PATH")
  Optional<String> trustStorePath;

  @ConfigProperty(name = "EXPORT_SERVICE_TRUST_STORE_PASSWORD")
  Optional<String> trustStorePassword;

  @ConfigProperty(name = "EXPORT_SERVICE_TRUST_STORE_TYPE")
  Optional<String> trustStoreType;

  @Produces
  @ApplicationScoped
  ClientConfig exportServiceClientConfig() {
    return new ClientConfig(
        EXPORT_SERVICE,
        psk,
        uri.filter(value -> !value.isBlank()).orElse(null),
        parseAuthenticated(authenticated.orElse(null)),
        trustStorePath.orElse(null),
        toPasswordChars(trustStorePassword.orElse(null)),
        trustStoreType.orElse(null),
        readTimeout);
  }

  @Produces
  @ApplicationScoped
  TokenProvider exportServiceTokenProvider(KesselProperties kesselProperties) {
    return new OidcTokenProvider(
        kesselProperties.authOidcIssuer().orElse(null),
        kesselProperties.authClientId().orElse(null),
        kesselProperties.authClientSecret().orElse(null));
  }

  @Produces
  @ApplicationScoped
  SwatchExportApi swatchExportApi(
      ClientConfig exportServiceClientConfig, TokenProvider tokenProvider) {
    return new SwatchExportApi(exportServiceClientConfig, tokenProvider);
  }

  @Produces
  @ApplicationScoped
  ExportDelegate exportDelegate(SwatchExportApi swatchExportApi) {
    return new ExportDelegateImpl(swatchExportApi);
  }

  @ApplicationScoped
  @Produces
  ExportRequestHandler exportService(
      ExportDelegate exportDelegate,
      RbacDelegate rbacDelegate,
      SubscriptionDataExporterService subscriptionDataExporterService,
      ObjectMapper objectMapper,
      CsvMapper csvMapper) {
    return new ExportRequestHandler(
        exportDelegate,
        rbacDelegate,
        List.of(subscriptionDataExporterService),
        new ConsoleCloudEventParser(objectMapper),
        new JsonExportFileWriter(objectMapper),
        new CsvExportFileWriter(csvMapper));
  }

  @ApplicationScoped
  @Produces
  @Typed(CsvMapper.class)
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

  private static Boolean parseAuthenticated(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return Boolean.parseBoolean(raw.trim());
  }

  private static char[] toPasswordChars(String password) {
    return password == null ? null : password.toCharArray();
  }
}
