/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.service.prometheus;

import static org.candlepin.subscriptions.metering.OpenShiftMetricsHelper.buildCoresMetricQueryResult;
import static org.candlepin.subscriptions.metering.OpenShiftMetricsHelper.buildLabelDataResult;
import static org.candlepin.subscriptions.metering.OpenShiftMetricsHelper.buildSubscriptionLabelsQueryResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;


@SpringBootTest
@ActiveProfiles({"openshift-metering-worker", "test"})
class PrometheusMeteringControllerTest {

    @MockBean
    private PrometheusService service;

    @MockBean
    private EventController eventController;

    @Autowired
    private PrometheusMetricsPropeties props;

    @Autowired
    private PrometheusMeteringController controller;


    private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

    private final String expectedAccount = "my-test-account";
    private final String expectedClusterId = "C1";
    private final String expectedSla = "Premium";
    private final String expectedUsage = "Production";

    @Test
    void testRetryWhenOpenshiftServiceReturnsError() throws Exception {
        String expectedLabelQuery = String.format(props.getOpenshift().getSubscriptionLabelPromQL(),
            expectedAccount);
        String expectedMetricQuery = String.format(props.getOpenshift().getMetricPromQL(), expectedAccount);

        QueryResult errorResponse = new QueryResult();
        errorResponse.setStatus(StatusType.ERROR);
        errorResponse.setError("FORCED!!");

        QueryResult metrics = buildCoresMetricQueryResult(expectedAccount, expectedClusterId,
            List.of(List.of(new BigDecimal(12312.345), new BigDecimal(24))));
        QueryResult labels = buildSubscriptionLabelsQueryResult(expectedAccount, expectedClusterId,
            expectedSla, expectedUsage, List.of(List.of(new BigDecimal(22222.22), new BigDecimal(1))));

        when(service.runRangeQuery(eq(expectedLabelQuery), any(), any(), any(), any()))
            .thenReturn(errorResponse, errorResponse, labels);
        when(service.runRangeQuery(eq(expectedMetricQuery), any(), any(), any(), any()))
            .thenReturn(metrics);

        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        // Query for labels will fail twice due to errorResponse, and will pass on the 3rd retry.
        verify(service, times(3)).runRangeQuery(eq(expectedLabelQuery), any(), any(), any(), any());
        // Query for metrics should only run once since the above failures prevent the query from happening.
        verify(service, times(1)).runRangeQuery(eq(expectedMetricQuery), any(), any(), any(), any());
    }

    @Test
    void datesAdjustedWhenReportingOpenShiftMetrics() throws Exception {
        OffsetDateTime start = clock.now().withSecond(30).withMinute(22);
        OffsetDateTime end = start.plusHours(4);

        QueryResult data = buildSubscriptionLabelsQueryResult(expectedAccount, expectedClusterId,
            expectedSla, expectedUsage, List.of(List.of(new BigDecimal(22222.22), new BigDecimal(1))));

        when(service.runRangeQuery(anyString(), any(), any(), any(), any())).thenReturn(data);

        controller.collectOpenshiftMetrics(expectedAccount, start, end);
        verify(service).runRangeQuery(
            String.format(props.getOpenshift().getMetricPromQL(), expectedAccount),
            clock.startOfHour(start), clock.endOfHour(end),
            props.getOpenshift().getStep(),
            props.getOpenshift().getQueryTimeout());
    }

    @Test
    @SuppressWarnings("indentation")
    void collectOpenShiftMetricsWillPersistEvents() throws Exception {
        BigDecimal time1 = BigDecimal.valueOf(123456.234);
        BigDecimal val1 = BigDecimal.valueOf(100L);
        BigDecimal time2 = BigDecimal.valueOf(222222.222);
        BigDecimal val2 = BigDecimal.valueOf(120L);

        QueryResult labels = buildSubscriptionLabelsQueryResult(expectedAccount, expectedClusterId,
            expectedSla, expectedUsage, List.of(List.of(time1, new BigDecimal(1)),
                List.of(time2, new BigDecimal(1))));

        QueryResult metrics = buildCoresMetricQueryResult(expectedAccount, expectedClusterId,
            List.of(List.of(time1, val1), List.of(time2, val2)));

        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getMetricPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(metrics);
        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getSubscriptionLabelPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(labels);

        OffsetDateTime start = clock.startOfCurrentHour();
        OffsetDateTime end = clock.endOfHour(start.plusDays(1));

        List<Event> expectedEvents = List.of(
            MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId, expectedSla,
                expectedUsage, clock.dateFromUnix(time1), val1.doubleValue()),
            MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId, expectedSla,
                expectedUsage, clock.dateFromUnix(time2), val2.doubleValue())
        );

        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(service).runRangeQuery(
            String.format(props.getOpenshift().getMetricPromQL(), expectedAccount),
            start, end, props.getOpenshift().getStep(),
            props.getOpenshift().getQueryTimeout());
        verify(eventController).saveAll(expectedEvents);
    }

    @Test
    void verifyOpenShiftEventsAreBatchedWhileBeingPersisted() throws Exception {
        OffsetDateTime end = clock.now();
        OffsetDateTime start = end.minusDays(2);

        props.getOpenshift().setEventBatchSize(5);
        // Create enough events to persist 2 times the batch size events, plus 2 to trigger
        // an extra flush at the end.
        List<List<BigDecimal>> recordedMetrics = new LinkedList<>();
        List<List<BigDecimal>> recordedLabelMetrics = new LinkedList<>();
        for (int i = 0; i < props.getOpenshift().getEventBatchSize() * 2 + 2; i++) {
            BigDecimal time = new BigDecimal(clock.now().toEpochSecond());
            recordedMetrics.add(List.of(time, new BigDecimal(12)));
            recordedLabelMetrics.add(List.of(time, new BigDecimal(1)));
        }
        assertEquals(12, recordedMetrics.size());
        assertEquals(12, recordedLabelMetrics.size());

        QueryResult labels = buildSubscriptionLabelsQueryResult(expectedAccount, expectedClusterId,
            expectedSla, expectedUsage, recordedLabelMetrics);

        QueryResult metrics = buildCoresMetricQueryResult(expectedAccount, expectedClusterId,
            recordedMetrics);

        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getMetricPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(metrics);
        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getSubscriptionLabelPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(labels);

        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(eventController, times(3)).saveAll(any());
    }

    // This test demonstrates that if the support label changes for a cluster, it will be reflected in
    // the events that are produced.
    @Test
    void testSlaChangeForCluster() throws Exception {
        BigDecimal time1 = BigDecimal.valueOf(clock.now().toEpochSecond());
        BigDecimal time2 = BigDecimal.valueOf(clock.now().plusMinutes(5).toEpochSecond());

        QueryResult labels = buildSubscriptionLabelsQueryResult(expectedAccount, expectedClusterId,
            "Premium", expectedUsage, List.of(List.of(time1, new BigDecimal(1))));
        labels.getData().addResultItem(buildLabelDataResult(expectedAccount, expectedClusterId,
            "Standard", expectedUsage, List.of(List.of(time2, new BigDecimal(1)))));

        BigDecimal expectedVal1 = new BigDecimal(12);
        BigDecimal expectedVal2 = new BigDecimal(16);
        QueryResult metrics = buildCoresMetricQueryResult(expectedAccount, expectedClusterId,
            List.of(List.of(time1, expectedVal1), List.of(time2, expectedVal2)));

        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getMetricPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(metrics);
        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getSubscriptionLabelPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(labels);

        Event event1 = MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId,
            "Premium", expectedUsage, clock.dateFromUnix(time1), expectedVal1.doubleValue());
        Event event2 = MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId,
            "Standard", expectedUsage, clock.dateFromUnix(time2), expectedVal2.doubleValue());
        List<Event> expectedEvents = List.of(event1, event2);

        OffsetDateTime start = clock.startOfCurrentHour();
        OffsetDateTime end = clock.endOfHour(start.plusDays(1));
        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(eventController).saveAll(expectedEvents);
    }

    /*
        A cores metric timestamp has to align with a subscription label metric timestamp in order
        for an event to be created for a cluster. This could potentially occur if the labels for
        the cluster has not yet been reported.
     */
    @Test
    void whenMetricAndLabelDatesDoNoLineUpAnEventIsNotCreatedForMetric() throws Exception {
        BigDecimal time1 = BigDecimal.valueOf(clock.now().toEpochSecond());
        BigDecimal time2 = BigDecimal.valueOf(clock.now().plusMinutes(5).toEpochSecond());

        QueryResult labels = buildSubscriptionLabelsQueryResult(expectedAccount, expectedClusterId,
            "Premium", expectedUsage, List.of(List.of(time1, new BigDecimal(1))));

        BigDecimal expectedVal1 = new BigDecimal(12);
        BigDecimal expectedVal2 = new BigDecimal(16);
        QueryResult metrics = buildCoresMetricQueryResult(expectedAccount, expectedClusterId,
            List.of(List.of(time1, expectedVal1), List.of(time2, expectedVal2)));

        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getMetricPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(metrics);
        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getSubscriptionLabelPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(labels);

        Event event = MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId,
            "Premium", expectedUsage, clock.dateFromUnix(time1), expectedVal1.doubleValue());
        List<Event> expectedEvents = List.of(event);

        OffsetDateTime start = clock.startOfCurrentHour();
        OffsetDateTime end = clock.endOfHour(start.plusDays(1));
        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(eventController).saveAll(expectedEvents);
    }

}
