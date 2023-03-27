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
package org.candlepin.subscriptions.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A class to test the tag profile contents. */
class TagProfileTest {

  private static final String ENG_PROD_69 = "69";

  private static final String RHEL_TAG = "RHEL";
  private static final String RHEL_DESKTOP_TAG = "RHEL for Desktop";
  private static final String RHEL_x86 = "RHEL for x86";
  private static final String OPENSHIFT_DEDICATED_TAG = "OpenShift-dedicated-metrics";
  public static final String OPENSHIFT_TAG = "OpenShift-metrics";
  public static final String RHOSAK_TAG = "rhosak";
  public static final String BASILISK_TAG = "BASILISK";

  private static final String OPENSHIFT_CLUSTER_ST = "OpenShift Cluster";
  private static final String KAFKA_CLUSTER_ST = "Kafka Cluster";
  public static final String BASILISK_ST = "BASILISK Instance";
  public static final String BILLING_MODEL_PAYG = "PAYG";

  private TagProfile tagProfile;

  @BeforeEach
  void setup() {
    tagProfile = buildTagProfile();

    // Manually invoke @PostConstruct so that the class is properly initialized.
    tagProfile.initLookups();
  }

  @Test
  void spotCheck() {
    assertNotNull(tagProfile);
    assertNotNull(tagProfile.getTagMappings());
    assertNotNull(tagProfile.getTagMetrics());
    assertNotNull(tagProfile.getTagMetaData());
  }

  @Test
  void findMetaDataByTag() {
    assertFalse(tagProfile.getTagMetaDataByTag("OpenShift-metrics").isEmpty());
  }

  @Test
  void testOptionalEmptyIfTagIsEmptyWhenLookingUpMetaData() {
    assertTrue(tagProfile.getTagMetaDataByTag("").isEmpty());
    assertTrue(tagProfile.getTagMetaDataByTag(null).isEmpty());
  }

  @Test
  void testOptionalEmptyIfMetaDataNotFoundForTag() {
    assertTrue(tagProfile.getTagMetaDataByTag("DOES_NOT_EXIST").isEmpty());
  }

  @Test
  void testGetTagMetricByProductTagAndUom() {
    Optional<TagMetric> metric = tagProfile.getTagMetric("OpenShift-metrics", Uom.CORES);
    assertFalse(metric.isEmpty());
    assertEquals("OpenShift-metrics", metric.get().getTag());
    assertEquals(Uom.CORES, metric.get().getUom());
  }

  @Test
  void testGetTagMetricByProductTagAndUomThrowsExceptionWhenDuplicateDefined() {
    tagProfile.getTagMetrics().add(tagProfile.getTagMetric("OpenShift-metrics", Uom.CORES).get());

    assertThrows(
        IllegalStateException.class, () -> tagProfile.getTagMetric("OpenShift-metrics", Uom.CORES));
  }

  @Test
  void testUomsForTag() {
    List<Uom> uoms = tagProfile.uomsForTag("OpenShift-metrics");
    assertEquals(2, uoms.size());
    assertTrue(uoms.containsAll(List.of(Uom.CORES, Uom.INSTANCE_HOURS)));
  }

  @Test
  void emptyListOfUomsWhenTagDoesNotExist() {
    List<Uom> uoms = tagProfile.uomsForTag("NOT_FOUND");
    assertTrue(uoms.isEmpty());
  }

  @Test
  void getSupportedMetricsForProduct() {
    Set<Uom> supportedMetricsForProduct =
        tagProfile.getSupportedMetricsForProduct("OpenShift-metrics");
    assertEquals(2, supportedMetricsForProduct.size());
    assertTrue(supportedMetricsForProduct.containsAll(List.of(Uom.CORES, Uom.INSTANCE_HOURS)));
  }

  @Test
  void getSupportedMetricsForProductThrowsExceptionWhenTagNotFound() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> tagProfile.getSupportedMetricsForProduct("NOT_FOUND"));
  }

  @Test
  void serviceTypesGetInitialized() {
    assertEquals(
        Set.of(OPENSHIFT_CLUSTER_ST, KAFKA_CLUSTER_ST, BASILISK_ST), tagProfile.getServiceTypes());
  }

  @Test
  void getTagMetaDataByServiceType() {
    Optional<TagMetaData> kafkaMetaData = tagProfile.getTagMetaDataByServiceType(KAFKA_CLUSTER_ST);
    assertTrue(kafkaMetaData.isPresent());
    assertEquals(KAFKA_CLUSTER_ST, kafkaMetaData.get().getServiceType());
    assertTrue(tagProfile.getTagMetaDataByServiceType("UNKNOWN").isEmpty());
  }

  @Test
  void getTagsByEngProduct() {
    assertEquals(Set.of(RHEL_TAG, RHEL_DESKTOP_TAG), tagProfile.getTagsByEngProduct(ENG_PROD_69));
    assertTrue(tagProfile.getTagsByEngProduct("11").isEmpty());
  }

  @Test
  void getTagsByRole() {
    assertEquals(Set.of(OPENSHIFT_DEDICATED_TAG), tagProfile.getTagsByRole(Role.OSD));
    assertTrue(tagProfile.getTagsByRole(Role.OCP).isEmpty());
  }

  @Test
  void getTagByArch() {
    assertEquals(Set.of(RHEL_x86), tagProfile.getTagsByArch("x86_64"));
    assertTrue(tagProfile.getTagsByArch(null).isEmpty());
  }

  @Test
  void getTagsForServiceType() {
    assertEquals(
        Set.of(OPENSHIFT_TAG, OPENSHIFT_DEDICATED_TAG),
        tagProfile.getTagsForServiceType(OPENSHIFT_CLUSTER_ST));
  }

  @Test
  void lookupProductNamesByTag() {
    Set<String> products = tagProfile.getOfferingProductNamesForTag(RHEL_DESKTOP_TAG);
    assertEquals(1, products.size());
    assertTrue(products.contains("RHEL Desktop"));
  }

  @Test
  void testIsProductPAYGEligibleTrue() {
    assertTrue(tagProfile.isProductPAYGEligible(ProductId.RHOSAK.toString()));
  }

  @Test
  void testIsProductPAYGEligibleFalse() {
    assertFalse(tagProfile.isProductPAYGEligible(ProductId.RHEL.toString()));
  }

  @Test
  void testIsContractEnabledForTag() {
    assertFalse(tagProfile.isTagContractEnabled(OPENSHIFT_TAG));
    assertTrue(tagProfile.isTagContractEnabled(BASILISK_TAG));
  }

  @Test
  void testInvalidContractEnabledConfigurationWhenBillingModelNotPayGo() {
    TagMetaData expectedMetaData =
        TagMetaData.builder().tags(Set.of()).billingModel("not-payg").contractEnabled(true).build();

    TagProfile profile =
        TagProfile.builder()
            .tagMappings(List.of())
            .tagMetrics(List.of())
            .tagMetaData(List.of(expectedMetaData))
            .build();

    Throwable e = assertThrows(IllegalStateException.class, () -> profile.initLookups());
    String expectedMessage =
        String.format(
            "A tag can only be configured as contractEnabled if billingModel=PAYG. %s",
            expectedMetaData);
    assertEquals(expectedMessage, e.getMessage());
  }

  @Test
  void throwsIllegalStateOnInitializationOnContractEnabledTagWithoutMonthlyBillingWindow() {
    TagProfile profile = buildTagProfile();
    profile
        .getTagMetric(BASILISK_TAG, Uom.INSTANCE_HOURS)
        .get()
        .setBillingWindow(BillingWindow.HOURLY);

    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> profile.initLookups());
    assertEquals(
        "Contract enabled tags must be configured with MONTHLY billing window: [BASILISK]",
        e.getMessage());
  }

  private static TagProfile buildTagProfile() {
    TagMapping tagMapping1 =
        TagMapping.builder()
            .value("69")
            .valueType("engId")
            .tags(Set.of(RHEL_TAG, RHEL_DESKTOP_TAG))
            .build();
    TagMapping tagMapping2 =
        TagMapping.builder().value("x86_64").valueType("arch").tags(Set.of(RHEL_x86)).build();
    TagMapping tagMapping3 =
        TagMapping.builder()
            .valueType("productName")
            .tags(Set.of(RHEL_DESKTOP_TAG))
            .value("RHEL Desktop")
            .build();

    TagMapping openshiftRoleMapping =
        TagMapping.builder()
            .tags(Set.of(OPENSHIFT_DEDICATED_TAG))
            .valueType("role")
            .value("osd")
            .build();

    Map<String, String> params = new HashMap<>();
    params.put("prometheusMetric", "cluster:usage:workload:capacity_physical_cpu_cores:max:5m");
    params.put("prometheusMetadataMetric", "subscription_labels");

    // Mutable List for purpose of modifying during testing.
    List<TagMetric> tagMetrics = new LinkedList<>();
    tagMetrics.add(
        TagMetric.builder()
            .tag("OpenShift-metrics")
            .uom(Uom.CORES)
            .metricId("m_cores")
            .queryParams(params)
            .build());
    tagMetrics.add(
        TagMetric.builder()
            .tag("OpenShift-metrics")
            .uom(Uom.INSTANCE_HOURS)
            .metricId("m_ihours")
            .queryParams(params)
            .build());
    tagMetrics.add(
        TagMetric.builder()
            .tag(BASILISK_TAG)
            .uom(Uom.INSTANCE_HOURS)
            .metricId("b_instance_hours")
            .queryParams(params)
            .build());

    TagMetaData openshiftClusterMetaData =
        TagMetaData.builder()
            .tags(Set.of(OPENSHIFT_TAG, OPENSHIFT_DEDICATED_TAG))
            .serviceType(OPENSHIFT_CLUSTER_ST)
            .finestGranularity(Granularity.HOURLY)
            .defaultSla(ServiceLevel.PREMIUM)
            .defaultUsage(Usage.PRODUCTION)
            .billingModel(BILLING_MODEL_PAYG)
            .build();

    TagMetaData kafkaClusterMetaData =
        TagMetaData.builder()
            .tags(Set.of(RHOSAK_TAG))
            .serviceType(KAFKA_CLUSTER_ST)
            .finestGranularity(Granularity.HOURLY)
            .defaultSla(ServiceLevel.PREMIUM)
            .defaultUsage(Usage.PRODUCTION)
            .billingModel(BILLING_MODEL_PAYG)
            .build();

    TagMetaData basiliskMetaData =
        TagMetaData.builder()
            .tags(Set.of(BASILISK_TAG))
            .serviceType(BASILISK_ST)
            .finestGranularity(Granularity.HOURLY)
            .defaultSla(ServiceLevel.PREMIUM)
            .defaultUsage(Usage.PRODUCTION)
            .billingModel(BILLING_MODEL_PAYG)
            .contractEnabled(true)
            .build();

    return TagProfile.builder()
        .tagMappings(List.of(tagMapping1, tagMapping2, tagMapping3, openshiftRoleMapping))
        .tagMetrics(tagMetrics)
        .tagMetaData(List.of(openshiftClusterMetaData, kafkaClusterMetaData, basiliskMetaData))
        .build();
  }
}
