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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.exception.SubscriptionsException;
import com.redhat.swatch.contract.resteasy.Page;
import com.redhat.swatch.contract.resteasy.Pageable;
import jakarta.ws.rs.core.Response;
import java.util.List;

public class InMemoryPager {
  private static final Integer DEFAULT_LIMIT = 50;

  private InMemoryPager() {
    /* intentionally empty */
  }

  public static <T> Page<T> paginate(List<T> items, Pageable pageable) {
    return new Page<>(paginate(items, pageable.getOffset(), pageable.getPageSize()), pageable);
  }

  public static <T> List<T> paginate(List<T> items, Integer offset, Integer limit) {
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

    if (offset > items.size()) {
      return List.of();
    }
    int lastIndex = Math.min(items.size(), offset + limit);
    return items.subList(offset, lastIndex);
  }
}
