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
package org.candlepin.subscriptions.util;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class InMemoryPager {
  public static <T> Page<T> paginate(List<T> items, Pageable pageable) {
    if (pageable == null) {
      return new PageImpl<>(items);
    }
    if (pageable.getOffset() > items.size()) {
      return new PageImpl<>(List.of(), pageable, items.size());
    }
    int offset = pageable.getPageNumber() * pageable.getPageSize();
    int lastIndex = Math.min(items.size(), offset + pageable.getPageSize());
    var pageItems = items.subList(offset, lastIndex);
    return new PageImpl<>(pageItems, pageable, items.size());
  }
}
