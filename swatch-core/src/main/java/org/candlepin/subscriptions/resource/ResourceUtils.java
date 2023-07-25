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
package org.candlepin.subscriptions.resource;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.security.InsightsUserPrincipal;
import org.candlepin.subscriptions.utilization.api.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Functionality common to both capacity and tally resources. */
public class ResourceUtils {

  private static final Integer DEFAULT_LIMIT = 50;

  public static final String ANY = "_ANY";

  private ResourceUtils() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Get the authenicated principal.
   *
   * <p>Typically one of {@link InsightsUserPrincipal}, {@link
   * org.candlepin.subscriptions.security.RhAssociatePrincipal}, or {@link
   * org.candlepin.subscriptions.security.X509Principal}
   *
   * @return the principal object
   */
  public static Object getPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null ? auth.getPrincipal() : null;
  }

  /**
   * Get the org ID of the authenticated user.
   *
   * @return orgId as a String
   */
  public static String getOrgId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    InsightsUserPrincipal principal = (InsightsUserPrincipal) auth.getPrincipal();
    return principal.getOrgId();
  }

  /**
   * Get the account number of the authenticated user.
   *
   * @return account number as a String
   */
  public static String getAccountNumber() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    InsightsUserPrincipal principal = (InsightsUserPrincipal) auth.getPrincipal();
    return principal.getAccountNumber();
  }

  /**
   * Validates offset and limit parameters and produces a {@link Pageable} for them.
   *
   * @param offset 0-based offset, can be null.
   * @param limit max number of items per-page, should be non-zero.
   * @return Pageable holding paging information.
   */
  @NotNull
  public static Pageable getPageable(Integer offset, Integer limit) {
    return getPageable(offset, limit, Sort.unsorted());
  }

  /**
   * Validates offset, limit, and sort parameters and produces a {@link Pageable} for them.
   *
   * @param offset 0-based offset, can be null.
   * @param limit max number of items per-page, should be non-zero.
   * @param sort sorting parameters.
   * @return Pageable holding paging and sorting information.
   */
  @NotNull
  public static Pageable getPageable(Integer offset, Integer limit, Sort sort) {
    if (limit == null) {
      limit = DEFAULT_LIMIT;
    }

    if (offset == null) {
      offset = 0;
    }

    if (offset % limit != 0) {
      throw new SubscriptionsException(
          ErrorCode.VALIDATION_FAILED_ERROR,
          Response.Status.BAD_REQUEST,
          "Offset must be divisible by limit",
          "Arbitrary offsets are not currently supported by this API");
    }
    return PageRequest.of(offset / limit, limit, sort);
  }

  /**
   * Uses Usage.ANY for a null value, otherwise returns db model equivalent of UsageType generated
   * enum
   *
   * @param usageType openapi generated equivalent enum for UsageType
   * @return Usage enum
   */
  public static Usage sanitizeUsage(UsageType usageType) {
    return Objects.isNull(usageType) ? Usage._ANY : Usage.fromString(usageType.toString());
  }

  /**
   * Uses ServiceLevel.ANY for a null value, otherwise returns db model equivalent of
   * ServiceLevelType generated enum
   *
   * @param sla string form of sla
   * @return ServiceLevel enum
   */
  public static ServiceLevel sanitizeServiceLevel(ServiceLevelType sla) {
    return Objects.isNull(sla) ? ServiceLevel._ANY : ServiceLevel.fromString(sla.toString());
  }

  /**
   * Uses BillingProvider.ANY for a null value, otherwise returns db model equivalent of
   * BillingProviderType generated enum
   *
   * @param billingProvider string form of billing provider
   * @return BilligProvider enum
   */
  public static BillingProvider sanitizeBillingProvider(BillingProviderType billingProvider) {
    return Objects.isNull(billingProvider)
        ? BillingProvider._ANY
        : BillingProvider.fromString(billingProvider.toString());
  }

  public static String sanitizeBillingAccountId(String billingAccountId) {

    return Objects.isNull(billingAccountId) || billingAccountId.isBlank() ? ANY : billingAccountId;
  }

  /**
   * Simple method to get around sonar complaint: java:S5411 - Boxed "Boolean" should be avoided in
   * boolean expressions
   */
  public static boolean sanitizeBoolean(Boolean value, boolean defaultValue) {
    if (Objects.isNull(value)) {
      return defaultValue;
    }
    return value;
  }

  public static String getCurrentIdentityHeader() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (Objects.isNull(requestAttributes)) {
      return null;
    }
    return ((ServletRequestAttributes) requestAttributes).getRequest().getHeader("x-rh-identity");
  }
}
