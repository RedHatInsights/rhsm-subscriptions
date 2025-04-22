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
package org.candlepin.subscriptions.tally.admin;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlRowSetResultSetExtractor;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.util.StringUtils;

@Slf4j
public class OrgIdToTallySnapshotMigration extends DataMigration {

  public static final SqlRowSetResultSetExtractor SQL_ROW_SET_RESULT_SET_EXTRACTOR =
      new SqlRowSetResultSetExtractor();

  public static final String UPDATE_SQL = "update tally_snapshots set org_id=? where id =?";

  private static final String ACCOUNT_SERVICE_QUERY =
      """
      select t.id, a.org_id, t.org_id as tally_org
      from tally_snapshots t
      left join account_config a on a.account_number = t.account_number
      where ?::uuid is null or t.id > ?::uuid
      order by t.id
      limit ?""";

  private final Counter counter;

  public OrgIdToTallySnapshotMigration(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
    super(jdbcTemplate, meterRegistry);
    counter = meterRegistry.counter("swatch_orgIdToTallysnapshot_migration");
  }

  @Override
  public SqlRowSet extract(String recordOffset, int batchSize) {
    return jdbcTemplate.query(
        ACCOUNT_SERVICE_QUERY,
        new Object[] {recordOffset, recordOffset, batchSize},
        new int[] {Types.VARCHAR, Types.VARCHAR, Types.NUMERIC},
        SQL_ROW_SET_RESULT_SET_EXTRACTOR);
  }

  @Override
  public String transformAndLoad(SqlRowSet data) {
    String lastSeenSnapshotId = null;
    List<Object[]> updateList = new ArrayList<>();
    int snapshotCount = 0;
    while (data.next()) {
      String snapshotId = data.getString("id");
      String orgId = data.getString("org_id");
      String tallyOrg = data.getString("tally_org");
      lastSeenSnapshotId = snapshotId;

      if (!StringUtils.hasText(tallyOrg)) {
        log.debug("Updating ownerId for tally snapshotId: {}", tallyOrg);
        updateList.add(new Object[] {orgId, snapshotId});
      }
      snapshotCount++;
    }
    jdbcTemplate.batchUpdate(UPDATE_SQL, updateList);
    counter.increment(snapshotCount);
    return lastSeenSnapshotId;
  }

  @Override
  public void recordCompleted() {
    // intentionally left blank
  }
}
