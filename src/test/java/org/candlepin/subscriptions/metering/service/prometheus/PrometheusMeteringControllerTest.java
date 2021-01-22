/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.metering.MeteringProperties;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResult;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class PrometheusMeteringControllerTest {

    @Mock
    private PrometheusService service;

    @Mock
    private EventController eventController;

    private MeteringProperties props = new MeteringProperties();

    private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

    private final String expectedAccount = "my-test-account";
    private final String expectedClusterId = "C1";
    private final String expectedSla = "Premium";

    @Test
    void testMeteringExceptionWhenServiceReturnsError() throws Exception {
        QueryResult errorResponse = new QueryResult();
        errorResponse.setStatus(StatusType.ERROR);
        errorResponse.setError("FORCED!!");

        when(service.getOpenshiftData(anyString(), any(), any())).thenReturn(errorResponse);

        PrometheusMeteringController controller = new PrometheusMeteringController(clock, props, service,
            eventController);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Throwable e = assertThrows(MeteringException.class, () -> controller.collectOpenshiftMetrics(
            "account", start, end));
        assertEquals("Unable to fetch openshift metrics: FORCED!!", e.getMessage());
    }

    @Test
    void datesRoundedDownToTheHourWhenReportingOpenShiftMetrics() throws Exception {
        OffsetDateTime start = clock.now().withSecond(30).withMinute(22);
        OffsetDateTime end = start.plusHours(4);
        QueryResult data = buildOpenShiftClusterQueryResult(expectedAccount, expectedClusterId, expectedSla,
            Arrays.asList(Arrays.asList(new BigDecimal(12312.345), new BigDecimal(24))));
        when(service.getOpenshiftData(eq(expectedAccount),
            any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(data);

        PrometheusMeteringController controller = new PrometheusMeteringController(clock, props, service,
            eventController);

        controller.collectOpenshiftMetrics(expectedAccount, start, end);
        verify(service).getOpenshiftData(expectedAccount, clock.startOfHour(start),
            clock.startOfHour(end));
    }

    @Test
    @SuppressWarnings("indentation")
    void collectOpenShiftMetricswillPersistEvents() throws Exception {
        BigDecimal time1 = BigDecimal.valueOf(123456.234);
        BigDecimal val1 = BigDecimal.valueOf(100L);
        BigDecimal time2 = BigDecimal.valueOf(222222.222);
        BigDecimal val2 = BigDecimal.valueOf(120L);

        QueryResult data = buildOpenShiftClusterQueryResult(expectedAccount, expectedClusterId, expectedSla,
            Arrays.asList(Arrays.asList(time1, val1), Arrays.asList(time2, val2)));
        when(service.getOpenshiftData(eq(expectedAccount),
            any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(data);

        OffsetDateTime start = clock.startOfHour(clock.now());
        OffsetDateTime end = start.plusDays(1);

        List<Event> expectedEvents = Arrays.asList(
            MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId, expectedSla,
                clock.dateFromUnix(time1), val1.doubleValue()),
            MeteringEventFactory.openShiftClusterCores(expectedAccount, expectedClusterId, expectedSla,
                clock.dateFromUnix(time2), val2.doubleValue())
        );

        PrometheusMeteringController controller = new PrometheusMeteringController(clock, props, service,
            eventController);
        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(service).getOpenshiftData(expectedAccount, start, end);
        verify(eventController).saveAll(expectedEvents);
    }

    @Test
    void verifyOpenShiftEventsAreBatchedWhileBeingPersisted() throws Exception {
        OffsetDateTime end = clock.now();
        OffsetDateTime start = end.minusDays(2);

        props.setEventBatchSize(5);
        // Create enough events to persist 2 times the batch size events, plus 2 to trigger
        // an extra flush at the end.
        List<List<BigDecimal>> recordedMetrics = new LinkedList<>();
        for (int i = 0; i < props.getEventBatchSize() * 2 + 2; i++) {
            recordedMetrics.add(Arrays.asList(new BigDecimal(111111.111), new BigDecimal(12)));
        }
        assertEquals(12, recordedMetrics.size());

        QueryResult data = buildOpenShiftClusterQueryResult("my-account", "my-cluster", "Production",
            recordedMetrics);
        when(service.getOpenshiftData(eq(expectedAccount),
            any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(data);

        PrometheusMeteringController controller = new PrometheusMeteringController(clock, props, service,
            eventController);
        controller.collectOpenshiftMetrics(expectedAccount, start, end);

        verify(eventController, times(3)).saveAll(any());
    }

    private QueryResult buildOpenShiftClusterQueryResult(String account, String clusterId, String sla,
        List<List<BigDecimal>> timeValueTuples) {
        QueryResultDataResult dataResult = new QueryResultDataResult()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", sla)
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
