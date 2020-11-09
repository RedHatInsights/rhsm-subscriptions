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
package org.candlepin.subscriptions.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurement;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.RoleProvider;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.AccountListSource;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.utilization.api.model.TallyReport;
import org.candlepin.subscriptions.utilization.api.model.TallyReportMeta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;

@SuppressWarnings("linelength")
@SpringBootTest
@TestPropertySource("classpath:/test.properties")
@WithMockRedHatPrincipal("123456")
public class TallyResourceTest {

    @MockBean
    TallySnapshotRepository repository;

    @MockBean
    PageLinkCreator pageLinkCreator;

    @MockBean
    AccountListSource accountListSource;

    @Autowired
    TallyResource resource;

    private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
    private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

    @BeforeEach
    public void setupTests() throws AccountListSourceException {
        when(accountListSource.containsReportingAccount(eq("account123456"))).thenReturn(true);
    }

    @Test
    public void testNullSlaQueryParameter() {

        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                Mockito.eq("account123456"),
                Mockito.eq("product1"),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.ANY),
                Mockito.eq(Usage.PRODUCTION),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));

        TallyReport report = resource.getTallyReport(
            "product1",
            Granularity.DAILY.name(),
            min,
            max,
            10,
            10,
            null,
            Usage.PRODUCTION.getValue()
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository).findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.ANY),
            Mockito.eq(Usage.PRODUCTION),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable)
        );

        assertMetadata(report.getMeta(), "product1", null,
            Usage.PRODUCTION.getValue(), Granularity.DAILY.name(), 1);

    }

    @Test
    public void testNullUsageQueryParameter() {

        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                Mockito.eq("account123456"),
                Mockito.eq("product1"),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage.ANY),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));

        TallyReport report = resource.getTallyReport(
            "product1",
            Granularity.DAILY.name(),
            min,
            max,
            10,
            10,
            ServiceLevel.PREMIUM.getValue(),
            null
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository).findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.PREMIUM),
            Mockito.eq(Usage.ANY),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable)
        );

        assertMetadata(report.getMeta(), "product1", ServiceLevel.PREMIUM.getValue(), null, Granularity.DAILY.name(),
            1);

    }

    @Test
    public void testUnsetSlaQueryParameter() {
        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                 Mockito.eq("account123456"),
                 Mockito.eq("product1"),
                 Mockito.eq(Granularity.DAILY),
                 Mockito.eq(ServiceLevel.UNSPECIFIED),
                 Mockito.eq(Usage.PRODUCTION),
                 Mockito.eq(min),
                 Mockito.eq(max),
                 Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));

        TallyReport report = resource.getTallyReport(
            "product1",
            Granularity.DAILY.name(),
            min,
            max,
            10,
            10,
            ServiceLevel.UNSPECIFIED.getValue(),
            Usage.PRODUCTION.getValue()
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository).findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.UNSPECIFIED),
            Mockito.eq(Usage.PRODUCTION),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable)
        );

        assertMetadata(report.getMeta(), "product1", "", Usage.PRODUCTION.getValue(), Granularity.DAILY.name(),
            1);
    }

    @Test
    public void testUnsetUsageQueryParameter() {
        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                Mockito.eq("account123456"),
                Mockito.eq("product1"),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage.UNSPECIFIED),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));

        TallyReport report = resource.getTallyReport(
            "product1",
            Granularity.DAILY.name(),
            min,
            max,
            10,
            10,
            ServiceLevel.PREMIUM.getValue(),
            Usage.UNSPECIFIED.getValue()
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository).findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.PREMIUM),
            Mockito.eq(Usage.UNSPECIFIED),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable)
        );

        assertMetadata(report.getMeta(), "product1", ServiceLevel.PREMIUM.getValue(), "", Granularity.DAILY.name(),
            1);
    }

    @Test
    public void testSetSlaAndUsageQueryParameters() {
        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                 Mockito.eq("account123456"),
                 Mockito.eq("product1"),
                 Mockito.eq(Granularity.DAILY),
                 Mockito.eq(ServiceLevel.PREMIUM),
                 Mockito.eq(Usage.PRODUCTION),
                 Mockito.eq(min),
                 Mockito.eq(max),
                 Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));
        TallyReport report = resource.getTallyReport(
            "product1",
            Granularity.DAILY.name(),
            min,
            max,
            10,
            10,
            ServiceLevel.PREMIUM.getValue(),
            Usage.PRODUCTION.getValue()
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository).findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.PREMIUM),
            Mockito.eq(Usage.PRODUCTION),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable)
        );

        assertMetadata(report.getMeta(), "product1", ServiceLevel.PREMIUM.getValue(),
            Usage.PRODUCTION.getValue(), Granularity.DAILY.name(), 1);
    }

    @Test
    public void testShouldUseQueryBasedOnHeaderAndParameters() throws Exception {
        TallySnapshot snap = new TallySnapshot();

        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.PREMIUM),
            Mockito.eq(Usage.PRODUCTION),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.any(Pageable.class)))
            .thenReturn(new PageImpl<>(Arrays.asList(snap)));
        TallyReport report = resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            10,
            10,
            ServiceLevel.PREMIUM.getValue(),
            Usage.PRODUCTION.getValue()
        );
        assertEquals(1, report.getData().size());

        Pageable expectedPageable = PageRequest.of(1, 10);
        Mockito.verify(repository)
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
            Mockito.eq("account123456"),
            Mockito.eq("product1"),
            Mockito.eq(Granularity.DAILY),
            Mockito.eq(ServiceLevel.PREMIUM),
            Mockito.eq(Usage.PRODUCTION),
            Mockito.eq(min),
            Mockito.eq(max),
            Mockito.eq(expectedPageable));
    }

    @Test
    void testShouldIgnoreHbiAwsWhenCloudigradeAwsPresent() {
        TallySnapshot snapshot = new TallySnapshot();
        HardwareMeasurement hbiMeasurement = new HardwareMeasurement();
        hbiMeasurement.setSockets(3);
        hbiMeasurement.setInstanceCount(3);
        HardwareMeasurement cloudigradeMeasurement = new HardwareMeasurement();
        cloudigradeMeasurement.setSockets(7);
        cloudigradeMeasurement.setInstanceCount(7);
        snapshot.setHardwareMeasurement(HardwareMeasurementType.AWS, hbiMeasurement);
        snapshot.setHardwareMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, cloudigradeMeasurement);

        org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot = snapshot
            .asApiSnapshot();

        assertEquals(7, apiSnapshot.getCloudInstanceCount().intValue());
        assertEquals(7, apiSnapshot.getCloudSockets().intValue());
    }

    @Test
    void testShouldAddHypervisorAndVirtual() {
        TallySnapshot snapshot = new TallySnapshot();
        HardwareMeasurement hypervisorMeasurement = new HardwareMeasurement();
        hypervisorMeasurement.setSockets(3);
        hypervisorMeasurement.setInstanceCount(3);
        HardwareMeasurement virtualMeasurement = new HardwareMeasurement();
        virtualMeasurement.setSockets(7);
        virtualMeasurement.setInstanceCount(7);
        snapshot.setHardwareMeasurement(HardwareMeasurementType.HYPERVISOR, hypervisorMeasurement);
        snapshot.setHardwareMeasurement(HardwareMeasurementType.VIRTUAL, virtualMeasurement);

        org.candlepin.subscriptions.utilization.api.model.TallySnapshot apiSnapshot = snapshot
            .asApiSnapshot();

        assertEquals(10, apiSnapshot.getHypervisorInstanceCount().intValue());
        assertEquals(10, apiSnapshot.getHypervisorSockets().intValue());
    }

    @Test
    public void testShouldThrowExceptionOnBadOffset() throws IOException {
        SubscriptionsException e = assertThrows(SubscriptionsException.class, () -> resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            11,
            10,
            ServiceLevel.PREMIUM.getValue(),
            Usage.PRODUCTION.getValue()
        ));
        assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void reportDataShouldGetFilledWhenPagingParametersAreNotPassed() {
        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                 Mockito.eq("account123456"),
                 Mockito.eq("product1"),
                 Mockito.eq(Granularity.DAILY),
                 Mockito.eq(ServiceLevel.ANY),
                 Mockito.eq(Usage.ANY),
                 Mockito.eq(min),
                 Mockito.eq(max),
                 Mockito.eq(null)))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        TallyReport report = resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            null,
            null,
            null,
            null
        );

        // Since nothing was returned from the DB, there should be one generated snapshot for each day
        // in the range.
        assertEquals(9, report.getData().size());
        report.getData().forEach(snap -> assertFalse(snap.getHasData()));
    }

    @Test
    void testEmptySnapshotFilledWithAllZeroes() {
        org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot =
            new org.candlepin.subscriptions.utilization.api.model.TallySnapshot();

        assertEquals(0, snapshot.getInstanceCount().intValue());
        assertEquals(0, snapshot.getCores().intValue());
        assertEquals(0, snapshot.getSockets().intValue());
        assertEquals(0, snapshot.getHypervisorInstanceCount().intValue());
        assertEquals(0, snapshot.getHypervisorCores().intValue());
        assertEquals(0, snapshot.getHypervisorSockets().intValue());
        assertEquals(0, snapshot.getCloudInstanceCount().intValue());
        assertEquals(0, snapshot.getCloudCores().intValue());
        assertEquals(0, snapshot.getCloudSockets().intValue());
    }

    @Test
    public void ensureBadRequestExceptionIsThrownWhenAnInvalidSlaParameterIsSpecified() {
        assertThrows(BadRequestException.class, () -> {
            resource.getTallyReport(
                "product1",
                "daily",
                min,
                max,
                null,
                null,
                "foo_sla",
                null
            );
        });
    }

    @Test
    @WithMockRedHatPrincipal(value = "123456", roles = {"ROLE_" + RoleProvider.SWATCH_ADMIN_ROLE})
    public void canReportWithOnlyReportingRole() {
        Mockito.when(repository
            .findByAccountNumberAndProductIdAndGranularityAndServiceLevelAndUsageAndSnapshotDateBetweenOrderBySnapshotDate(
                 Mockito.eq("account123456"),
                 Mockito.eq("product1"),
                 Mockito.eq(Granularity.DAILY),
                 Mockito.eq(ServiceLevel.ANY),
                 Mockito.eq(Usage.ANY),
                 Mockito.eq(min),
                 Mockito.eq(max),
                 Mockito.eq(null)))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        TallyReport report = resource.getTallyReport(
            "product1",
            "daily",
            min,
            max,
            null,
            null,
            null,
            null
        );
        assertNotNull(report);
    }

    @Test
    @WithMockRedHatPrincipal("1111")
    public void testAccessDeniedWhenAccountIsNotWhitelisted() {
        assertThrows(AccessDeniedException.class, () -> {
            resource.getTallyReport(
                "product1",
                "daily",
                min,
                max,
                null,
                null,
                null,
                null
            );
        });
    }

    @Test
    @WithMockRedHatPrincipal(value = "123456", roles = {})
    public void testAccessDeniedWhenUserIsNotAnAdmin() {
        assertThrows(AccessDeniedException.class, () -> {
            resource.getTallyReport(
                "product1",
                "daily",
                min,
                max,
                null,
                null,
                null,
                null
            );
        });
    }

    private void assertMetadata(TallyReportMeta meta, String expectedProd, String expectedSla,
        String expectedUsage, String expectedGranularity, Integer expectedCount) {
        assertEquals(expectedProd, meta.getProduct());
        assertEquals(expectedSla, meta.getServiceLevel());
        assertEquals(expectedUsage, meta.getUsage());
        assertEquals(expectedGranularity, meta.getGranularity());
        assertEquals(expectedCount, meta.getCount());

    }
}
