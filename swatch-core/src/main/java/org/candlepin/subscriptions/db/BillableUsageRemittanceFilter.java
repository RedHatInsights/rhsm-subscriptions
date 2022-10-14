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
package org.candlepin.subscriptions.db;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK_;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity_;

/**
 * A filter used to find {@link org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity}
 * objects via the {@link BillableUsageRemittanceRepository}. Any filter value with a null value
 * will not be checked.
 */
@Builder
@Getter
@Setter
public class BillableUsageRemittanceFilter {
  private String productId;
  private String account;
  private String orgId;
  private String metricId;
  private String billingProvider;
  private String billingAccountId;
  private OffsetDateTime beginning;
  private OffsetDateTime ending;

  public List<SearchCriteria> getSearchCriteria() {
    List<SearchCriteria> criteria = new LinkedList<>();

    equalIfNotNull(criteria, BillableUsageRemittanceEntityPK_.ACCOUNT_NUMBER, this.account);
    equalIfNotNull(criteria, BillableUsageRemittanceEntity_.ORG_ID, this.orgId);
    equalIfNotNull(criteria, BillableUsageRemittanceEntityPK_.PRODUCT_ID, this.productId);
    equalIfNotNull(criteria, BillableUsageRemittanceEntityPK_.METRIC_ID, this.metricId);
    equalIfNotNull(
        criteria, BillableUsageRemittanceEntityPK_.BILLING_PROVIDER, this.billingProvider);
    equalIfNotNull(
        criteria, BillableUsageRemittanceEntityPK_.BILLING_ACCOUNT_ID, this.billingAccountId);

    if (Objects.nonNull(this.beginning)) {
      criteria.add(
          new SearchCriteria(
              BillableUsageRemittanceEntity_.REMITTANCE_DATE,
              beginning,
              SearchOperation.AFTER_OR_ON));
    }

    if (Objects.nonNull(this.ending)) {
      criteria.add(
          new SearchCriteria(
              BillableUsageRemittanceEntity_.REMITTANCE_DATE,
              ending,
              SearchOperation.BEFORE_OR_ON));
    }

    return criteria;
  }

  private void equalIfNotNull(
      List<SearchCriteria> existingCriteria, String attribute, String value) {
    if (Objects.nonNull(value)) {
      existingCriteria.add(new SearchCriteria(attribute, value, SearchOperation.EQUAL));
    }
  }
}
