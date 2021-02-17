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
package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.files.ProductProfileRegistry;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.tally.roller.BaseSnapshotRoller;
import org.candlepin.subscriptions.tally.roller.DailySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.HourlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.MonthlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.QuarterlySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.WeeklySnapshotRoller;
import org.candlepin.subscriptions.tally.roller.YearlySnapshotRoller;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Produces usage snapshot for all configured accounts.
 */
@Component
public class UsageSnapshotProducer {

    private static final Logger log = LoggerFactory.getLogger(UsageSnapshotProducer.class);

    private final HourlySnapshotRoller hourlyRoller;
    private final DailySnapshotRoller dailyRoller;
    private final WeeklySnapshotRoller weeklyRoller;
    private final MonthlySnapshotRoller monthlyRoller;
    private final YearlySnapshotRoller yearlyRoller;
    private final QuarterlySnapshotRoller quarterlyRoller;
    private final String tallySummaryTopic;
    private final KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate;

    @Autowired
    public UsageSnapshotProducer(TallySnapshotRepository tallyRepo, ApplicationClock clock,
        ProductProfileRegistry registry, ApplicationProperties props,
        KafkaTemplate<String, TallySummary> tallySummaryKafkaTemplate) {
        hourlyRoller = new HourlySnapshotRoller(tallyRepo, clock, registry);
        dailyRoller = new DailySnapshotRoller(tallyRepo, clock, registry);
        weeklyRoller = new WeeklySnapshotRoller(tallyRepo, clock, registry);
        monthlyRoller = new MonthlySnapshotRoller(tallyRepo, clock, registry);
        yearlyRoller = new YearlySnapshotRoller(tallyRepo, clock, registry);
        quarterlyRoller = new QuarterlySnapshotRoller(tallyRepo, clock, registry);
        tallySummaryTopic = props.getTallySummaryTopic();
        this.tallySummaryKafkaTemplate = tallySummaryKafkaTemplate;
    }

    @Transactional
    public void produceSnapshotsFromCalculations(Collection<String> accounts,
        Collection<AccountUsageCalculation> accountCalcs) {
        Stream<BaseSnapshotRoller> rollers = Stream.of(hourlyRoller, dailyRoller, weeklyRoller, monthlyRoller,
            quarterlyRoller, yearlyRoller);
        var newAndUpdatedSnapshots = rollers
            .map(roller -> roller.rollSnapshots(accounts, accountCalcs))
            .flatMap(Collection::stream)
            .collect(Collectors.groupingBy(TallySnapshot::getAccountNumber));
        produceTallySummaryMessages(newAndUpdatedSnapshots);
        log.info("Finished producing snapshots for all accounts.");
    }

    public void produceTallySummaryMessages(Map<String, List<TallySnapshot>> newAndUpdatedSnapshots) {
        newAndUpdatedSnapshots.entrySet().stream()
            .map(entry -> createTallySummary(entry.getKey(), entry.getValue())).forEach(tallySummary -> {
                log.info("Producing message of List<TallySummary>");
                tallySummaryKafkaTemplate.send(tallySummaryTopic, tallySummary);
            });
    }

    private TallySummary createTallySummary(String accountNumber, List<TallySnapshot> tallySnapshots) {
        var mappedSnapshots = tallySnapshots.stream()
            .map(this::mapTallySnapshot)
            .collect(Collectors.toList());
        return new TallySummary()
            .withAccountNumber(accountNumber)
            .withTallySnapshots(mappedSnapshots);
    }

    private org.candlepin.subscriptions.json.TallySnapshot mapTallySnapshot(TallySnapshot tallySnapshot) {

        var granularity = org.candlepin.subscriptions.json.TallySnapshot.Granularity
            .fromValue(tallySnapshot.getGranularity().getValue());

        var sla = org.candlepin.subscriptions.json.TallySnapshot.Sla
            .fromValue(tallySnapshot.getServiceLevel().getValue());

        return new org.candlepin.subscriptions.json.TallySnapshot()
            .withGranularity(granularity)
            .withId(tallySnapshot.getId())
            .withProductId(tallySnapshot.getProductId())
            .withSnapshotDate(tallySnapshot.getSnapshotDate())
            .withSla(sla)
            .withTallyMeasurements(mapMeasurements(tallySnapshot.getTallyMeasurements()));
    }

    private List<TallyMeasurement> mapMeasurements(Map<TallyMeasurementKey, Double> tallyMeasurements) {
        return tallyMeasurements.entrySet().stream()
            .map(entry -> new TallyMeasurement()
            .withHardwareMeasurementType(entry.getKey().getMeasurementType().toString())
            .withUom(TallyMeasurement.Uom.fromValue(entry.getKey().getUom().value()))
            .withValue(entry.getValue()))
            .collect(Collectors.toList());
    }
}
