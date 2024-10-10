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
package com.redhat.swatch.contract.resteasy;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Pageable {
  final Integer pageSize;
  final Integer offset;
  final Integer size;

  public Integer getPageSize() {
    return pageSize;
  }

  public Integer getOffset() {
    return offset;
  }

  public Integer getNextOffset() {
    if (pageSize == null) {
      return 0;
    }
    return offset + pageSize;
  }

  public Integer getPreviousOffset() {
    if (pageSize == null) {
      return 0;
    }
    return offset - pageSize;
  }

  public Integer getLastOffset() {
    if (size <= pageSize) {
      return 0;
    }
    return size - (size % pageSize) - 1;
  }
}
