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
package org.candlepin.subscriptions.tally;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.rhmarketplace.RhMarketplacePayloadMapper;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.validator.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class InternalTallyController {
  private static final Logger log = LoggerFactory.getLogger(InternalTallyController.class);
  private final SnapshotSummaryProducer summaryProducer;
  private final SnapshotSummaryProducer paygSummaryProducer;
  private final TallySnapshotRepository snapshotRepository;
  private final TagProfile tagProfile;
  private final ApplicationClock clock;

  public InternalTallyController(
      @Qualifier("summaryProducer") SnapshotSummaryProducer summaryProducer,
      @Qualifier("paygSnapshotSummaryProducer") SnapshotSummaryProducer paygSummaryProducer,
      TallySnapshotRepository snapshotRepository,
      TagProfile tagProfile,
      ApplicationClock clock) {
    this.summaryProducer = summaryProducer;
    this.paygSummaryProducer = paygSummaryProducer;
    this.snapshotRepository = snapshotRepository;
    this.tagProfile = tagProfile;
    this.clock = clock;
  }

  /* Ideally we would have the Bean Validator annotation @Valid at the REST endpoint that calls this
   * method.  It is possible to enable bean validation annotations in the OpenApi generator but
   * it appears (documentation is non-existent) that the only annotations that you can apply are
   * very basic ones like @Pattern or @Size (especially for the jaxrs-spec generator that we are
   * currently using -- the Spring specific generators seem slightly more flexible).  In theory,
   * the validations could be applied with a META-INF/validations.xml file (see
   * https://guntherrotsch.github.io/blog_2020/openapi-bean-validation.html) but that seems even
   * less desirable to me than applying the validation one layer down in the application.  I
   * prefer having the annotations as they are visible in the code as opposed to tucked away in
   * an XML file.
   */
  public int resendTallySnapshots(List<@Uuid String> uuids) {
    var snapshotIds = uuids.stream().map(UUID::fromString).collect(Collectors.toList());
    var snapshots = snapshotRepository.findAllById(snapshotIds);
    log.info("Resending tally snapshots for {} messages", snapshots.size());
    Map<String, List<TallySnapshot>> totalSnapshots =
        snapshots.stream().collect(Collectors.groupingBy(TallySnapshot::getAccountNumber));
    summaryProducer.produceTallySummaryMessages(totalSnapshots);
    return snapshots.size();
  }

  @Transactional
  public int emitPaygRollups(LocalDate date) {
    var paygProducts = tagProfile.getTagsForBillingModel(RhMarketplacePayloadMapper.PAYG_BILLING);
    var start = clock.startOfDayUTC(date);
    var end = clock.endOfDayUTC(date);

    // Streams from Spring Data sources need to be read with try-with-resources statements
    try (Stream<TallySnapshot> snapshots =
        snapshotRepository.findByProductIdInAndSnapshotDateBetween(paygProducts, start, end)) {
      Map<String, List<TallySnapshot>> mappedSnapshots =
          snapshots.collect(Collectors.groupingBy(TallySnapshot::getAccountNumber));

      paygSummaryProducer.produceTallySummaryMessages(mappedSnapshots);

      return mappedSnapshots.entrySet().stream()
          .flatMapToInt(x -> IntStream.of(x.getValue().size()))
          .sum();
    }
  }
}
