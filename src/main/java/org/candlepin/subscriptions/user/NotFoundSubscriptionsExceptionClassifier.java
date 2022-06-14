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
package org.candlepin.subscriptions.user;

import javax.ws.rs.core.Response.Status;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.springframework.classify.Classifier;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;

/**
 * This is a {@link Classifier} that examines a Throwable and returns a RetryPolicy to use based on
 * that Throwable. The purpose of this class is to introspect SubscriptionsExceptions that are
 * thrown by {@link AccountService}. SubscriptionsExceptions are thrown in two circumstances: if an
 * account is non-found or if there was some request or server error. For an account that is not
 * found, the server returns a 204 No Content, If the account isn't found, then we don't want to
 * retry. This Classifier looks at the HTTP response status in the exception and if the status is
 * 204, it returns a NeverRetryPolicy. Otherwise, it returns a default that is set during
 * construction.
 */
public class NotFoundSubscriptionsExceptionClassifier
    implements Classifier<Throwable, RetryPolicy> {
  private final RetryPolicy defaultPolicy;

  public NotFoundSubscriptionsExceptionClassifier(RetryPolicy defaultPolicy) {
    this.defaultPolicy = defaultPolicy;
  }

  @Override
  public RetryPolicy classify(Throwable classifiable) {
    if (classifiable instanceof SubscriptionsException) {
      var subscriptionsException = (SubscriptionsException) classifiable;
      if (Status.NO_CONTENT.equals(subscriptionsException.getStatus())) {
        return new NeverRetryPolicy();
      }
    }

    return defaultPolicy;
  }
}
