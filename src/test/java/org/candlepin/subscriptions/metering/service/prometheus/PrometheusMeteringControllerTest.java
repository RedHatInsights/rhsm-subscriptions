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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResult;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;


@SpringBootTest
@ActiveProfiles({"openshift-metering-worker", "test"})
class PrometheusMeteringControllerTest {

    @MockBean
    private PrometheusService service;

    @MockBean
    private EventController eventController;

    @Autowired
    private PrometheusMetricsProperties props;

    @Autowired
    private PrometheusMeteringController controller;


    private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

    private final String expectedAccount = "my-test-account";
    private final String expectedClusterId = "C1";
    private final String expectedSla = "Premium";
    private final String expectedUsage = "Production";
    private final String expectedRole = "ocm";

    @Test
    void testRetryWhenOpenshiftServiceReturnsError() throws Exception {
        QueryResult errorResponse = new QueryResult();
        errorResponse.setStatus(StatusType.ERROR);
        errorResponse.setError("FORCED!!");

        QueryResult good = buildOpenShiftClusterQueryResult(expectedAccount, expectedClusterId, expectedSla,
            expectedUsage, List.of(List.of(new BigDecimal(12312.345), new BigDecimal(24))));

        when(service.runRangeQuery(anyString(), any(), any(), any(), any()))
            .thenReturn(errorResponse, errorResponse, good);

        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        controller.collectOpenshiftMetrics("account", start, end);
        verify(service, times(3)).runRangeQuery(anyString(), any(), any(), any(), any());
    }

    @Test
    void datesAdjustedWhenReportingOpenShiftMetrics() throws Exception {
        OffsetDateTime start = clock.now().withSecond(30).withMinute(22);
        OffsetDateTime end = start.plusHours(4);
        QueryResult data = buildOpenShiftClusterQueryResult(expectedAccount, expectedClusterId, expectedSla,
            expectedUsage, List.of(List.of(new BigDecimal(12312.345), new BigDecimal(24))));
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

        QueryResult data = buildOpenShiftClusterQueryResult(expectedAccount, expectedClusterId, expectedSla,
            expectedUsage, List.of(List.of(time1, val1), List.of(time2, val2)));
        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getMetricPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(data);

        OffsetDateTime start = clock.startOfCurrentHour();
        OffsetDateTime end = clock.endOfHour(start.plusDays(1));

        List<Event> expectedEvents = List.of(
            MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId, expectedSla,
                expectedUsage, expectedRole,
                clock.dateFromUnix(time1).minusSeconds(props.getOpenshift().getStep()),
                clock.dateFromUnix(time1), val1.doubleValue()),
            MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId, expectedSla,
                expectedUsage, expectedRole,
                clock.dateFromUnix(time2).minusSeconds(props.getOpenshift().getStep()),
                clock.dateFromUnix(time2), val2.doubleValue())
        );

        ArgumentCaptor<Collection> saveCaptor = ArgumentCaptor.forClass(Collection.class);
        doNothing().when(eventController).saveAll(saveCaptor.capture());

        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(service).runRangeQuery(
            String.format(props.getOpenshift().getMetricPromQL(), expectedAccount),
            start, end, props.getOpenshift().getStep(),
            props.getOpenshift().getQueryTimeout());
        verify(eventController).saveAll(any());

        // Attempted to verify the eventController.saveAll(events) but
        // couldn't find a way to get mockito to match on the collection
        // of HashMap.Value. Using a capture works just as well, but is a less convenient.
        assertEquals(expectedEvents.size(), saveCaptor.getValue().size());
        assertTrue(saveCaptor.getValue().containsAll(expectedEvents));
    }

    @Test
    void verifyExistingEventsAreUpdatedWhenReportedByPrometheusAndDeletedIfStale() {
        BigDecimal time1 = BigDecimal.valueOf(123456.234);
        BigDecimal val1 = BigDecimal.valueOf(100L);
        BigDecimal time2 = BigDecimal.valueOf(222222.222);
        BigDecimal val2 = BigDecimal.valueOf(120L);

        QueryResult data = buildOpenShiftClusterQueryResult(expectedAccount, expectedClusterId, expectedSla,
            expectedUsage, List.of(List.of(time1, val1), List.of(time2, val2)));
        when(service.runRangeQuery(
            eq(String.format(props.getOpenshift().getMetricPromQL(), expectedAccount)),
            any(), any(), any(), any())).thenReturn(data);

        OffsetDateTime start = clock.startOfCurrentHour();
        OffsetDateTime end = clock.endOfHour(start.plusDays(1));

        Event updatedEvent = MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId,
            expectedSla, expectedUsage, expectedRole,
            clock.dateFromUnix(time1).minusSeconds(props.getOpenshift().getStep()),
            clock.dateFromUnix(time1),
            val1.doubleValue());

        List<Event> expectedEvents = List.of(updatedEvent,
            MeteringEventFactory.openShiftClusterCores(expectedAccount,
            expectedClusterId, expectedSla, expectedUsage, expectedRole,
            clock.dateFromUnix(time2).minusSeconds(props.getOpenshift().getStep()),
            clock.dateFromUnix(time2), val2.doubleValue()));

        Event purgedEvent = MeteringEventFactory.openShiftClusterCores(expectedAccount,
            "CLUSTER_NO_LONGER_EXISTS", expectedSla, expectedUsage, expectedRole,
            clock.dateFromUnix(time1).minusSeconds(props.getOpenshift().getStep()),
            clock.dateFromUnix(time1), val1.doubleValue());

        List<Event> existingEvents = List.of(
            // This event will get updated by the incoming data from prometheus.
            MeteringEventFactory.openShiftClusterCores(expectedAccount,
            expectedClusterId, expectedSla, expectedUsage, expectedRole, updatedEvent.getTimestamp(),
            updatedEvent.getExpiration().get(), 144.4),
            // This event should get purged because prometheus did not report this cluster.
            purgedEvent
        );
        when(eventController.mapEventsInTimeRange(expectedAccount,
            MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_SOURCE,
            MeteringEventFactory.OPENSHIFT_CLUSTER_EVENT_TYPE,
            start,
            end
        ))
            .thenReturn(existingEvents.stream().collect(Collectors.toMap(
            EventKey::fromEvent, Function.identity())));

        ArgumentCaptor<Collection> saveCaptor = ArgumentCaptor.forClass(Collection.class);
        doNothing().when(eventController).saveAll(saveCaptor.capture());

        ArgumentCaptor<Collection> purgeCaptor = ArgumentCaptor.forClass(Collection.class);
        doNothing().when(eventController).deleteEvents(purgeCaptor.capture());

        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(service).runRangeQuery(
            String.format(props.getOpenshift().getMetricPromQL(), expectedAccount),
            start, end, props.getOpenshift().getStep(),
            props.getOpenshift().getQueryTimeout());
        verify(eventController).saveAll(any());
        verify(eventController).deleteEvents(any());

        // Attempted to verify the eventController calls below, but
        // couldn't find a way to get mockito to match on collection of HashMap.Value.
        // Using a capture works just as well, but is a less convenient.
        assertEquals(expectedEvents.size(), saveCaptor.getValue().size());
        assertTrue(saveCaptor.getValue().containsAll(expectedEvents));

        assertEquals(1, purgeCaptor.getValue().size());
        assertTrue(purgeCaptor.getValue().contains(purgedEvent));
    }

    private QueryResult buildOpenShiftClusterQueryResult(String account, String clusterId, String sla,
        String usage, List<List<BigDecimal>> timeValueTuples) {
        QueryResultDataResult dataResult = new QueryResultDataResult()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", sla)
            .putMetricItem("usage", usage)
            .putMetricItem("ebs_account", account);

        // NOTE: A tuple is [unix_time,value]
        timeValueTuples.forEach(tuple -> dataResult.addValuesItem(tuple));

        return new QueryResult()
        .status(StatusType.SUCCESS)
        .data(
            new QueryResultData()
            .resultType(ResultType.MATRIX)
            .addResultItem(dataResult)
        );
    }
}
