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
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResult;
import org.candlepin.subscriptions.prometheus.model.ResultType;
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
    private PrometheusMetricsProperties props;

    @Autowired
    private PrometheusMeteringController controller;


    private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

    private final String expectedAccount = "my-test-account";
    private final String expectedClusterId = "C1";
    private final String expectedSla = "Premium";
    private final String expectedUsage = "Production";

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
        for (int i = 0; i < props.getOpenshift().getEventBatchSize() * 2 + 2; i++) {
            recordedMetrics.add(List.of(new BigDecimal(111111.111), new BigDecimal(12)));
        }
        assertEquals(12, recordedMetrics.size());

        QueryResult data = buildOpenShiftClusterQueryResult("my-account", "my-cluster", expectedSla,
            expectedUsage, recordedMetrics);
        when(service.runRangeQuery(eq(String.format(props.getOpenshift().getMetricPromQL(),
            expectedAccount)), any(), any(), any(), any())).thenReturn(data);

        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(eventController, times(3)).saveAll(any());
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
