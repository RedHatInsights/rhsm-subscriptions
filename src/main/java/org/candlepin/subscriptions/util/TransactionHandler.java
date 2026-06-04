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

import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * TransactionHandler service is used as a work around to Spring AOP's limitation of not being able
 * to call a Transactional method within the same class as the calling method. See:
 * https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
 */
@Service
public class TransactionHandler {

  @Transactional()
  public <T> T runInTransaction(Supplier<T> supplier) {
    return supplier.get();
  }

  @Transactional(TxType.REQUIRES_NEW)
  public <T> T runInNewTransaction(Supplier<T> supplier) {
    return supplier.get();
  }
}
