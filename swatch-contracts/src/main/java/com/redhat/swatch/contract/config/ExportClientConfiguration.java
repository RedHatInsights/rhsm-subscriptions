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
import com.redhat.swatch.clients.ClientConfigBuilder;
import com.redhat.swatch.clients.ClientConfigMode;
import com.redhat.swatch.clients.ClientConnection;
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
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;

/**
 * Quarkus wiring for the shared export-service client ({@link SwatchExportApi}). Logic lives in
 * {@code base-client} / {@code export-client}; this class only binds Clowder/OIDC config.
 */
@Slf4j
@ApplicationScoped
public class ExportClientConfiguration {

  public static final String EXPORT_SERVICE = "export-service";

  @ConfigProperty(name = "EXPORT_SERVICE_ENDPOINT_MODE", defaultValue = "auto")
  ClientConfigMode mode;

  @ConfigProperty(name = "SWATCH_EXPORT_PSK", defaultValue = "placeholder")
  String psk;

  @ConfigProperty(name = "EXPORT_SERVICE_READ_TIMEOUT", defaultValue = "120s")
  Duration readTimeout;

  @ConfigProperty(name = "EXPORT_SERVICE_ENDPOINT")
  Optional<String> v1Uri;

  @ConfigProperty(
      name = "clowder.optional-private-endpoints.export-service-service.trust-store-path")
  Optional<String> v1TrustStorePath;

  @ConfigProperty(
      name = "clowder.optional-private-endpoints.export-service-service.trust-store-password")
  Optional<String> v1TrustStorePassword;

  @ConfigProperty(
      name = "clowder.optional-private-endpoints.export-service-service.trust-store-type")
  Optional<String> v1TrustStoreType;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_URI")
  Optional<String> v2Uri;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_AUTHENTICATED")
  Optional<String> v2Authenticated;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_TRUST_STORE_PATH")
  Optional<String> v2TrustStorePath;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_TRUST_STORE_PASSWORD")
  Optional<String> v2TrustStorePassword;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_TRUST_STORE_TYPE")
  Optional<String> v2TrustStoreType;

  @Produces
  @ApplicationScoped
  ClientConfig exportServiceClientConfig() {
    ClientConnection primary =
        new ClientConnection(
            "v2",
            v2Uri.orElse(null),
            v2TrustStorePath.orElse(null),
            toPasswordChars(v2TrustStorePassword.orElse(null)),
            v2TrustStoreType.orElse(null),
            parseAuthenticated(v2Authenticated.orElse(null)));
    ClientConnection fallback =
        new ClientConnection(
            "v1",
            v1Uri.orElse(null),
            v1TrustStorePath.orElse(null),
            toPasswordChars(v1TrustStorePassword.orElse(null)),
            v1TrustStoreType.orElse(null),
            null);
    return ClientConfigBuilder.with(EXPORT_SERVICE, psk, primary, fallback)
        .readTimeout(readTimeout)
        .build(mode);
  }

  @Produces
  @ApplicationScoped
  TokenProvider exportServiceTokenProvider(KesselProperties kesselProperties) {
    OAuth2ClientCredentials credentials = initializeOAuth2Credentials(kesselProperties);
    return () -> {
      if (credentials == null) {
        throw new IllegalStateException("OAuth2 client credentials are not configured");
      }
      try {
        return credentials.getToken().accessToken();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to obtain OAuth2 access token", e);
      }
    };
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

  private static OAuth2ClientCredentials initializeOAuth2Credentials(KesselProperties properties) {
    var issuerUrl = properties.authOidcIssuer().filter(s -> !s.isBlank());
    var clientId = properties.authClientId().filter(s -> !s.isBlank());
    var clientSecret = properties.authClientSecret().filter(s -> !s.isBlank());
    if (issuerUrl.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()) {
      log.info(
          "OAuth2 client credentials not configured; authenticated export-service calls will be"
              + " unavailable");
      return null;
    }
    try {
      var discovery = OIDCDiscovery.fetchOIDCDiscovery(issuerUrl.get());
      var credentials =
          new OAuth2ClientCredentials(
              new ClientConfigAuth(clientId.get(), clientSecret.get(), discovery.tokenEndpoint()));
      log.info("OAuth2 client credentials initialized for export-service Bearer auth");
      return credentials;
    } catch (Exception e) {
      log.warn(
          "Failed to initialize OAuth2 client credentials; authenticated export-service calls will"
              + " be unavailable",
          e);
      return null;
    }
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
