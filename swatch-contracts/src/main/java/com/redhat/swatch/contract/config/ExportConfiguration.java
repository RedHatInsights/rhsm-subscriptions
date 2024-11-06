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
import com.redhat.swatch.contract.service.export.SubscriptionDataExporterService;
import com.redhat.swatch.export.CsvExportFileWriter;
import com.redhat.swatch.export.ExportRequestHandler;
import com.redhat.swatch.export.JsonExportFileWriter;
import com.redhat.swatch.export.api.ExportDelegate;
import com.redhat.swatch.export.api.RbacDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import java.util.List;

@ApplicationScoped
public class ExportConfiguration {

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
}
