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
package com.redhat.swatch.configuration.registry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.*;

/**
 * Variant is a mutually exclusive "edition" of a subscription, having the same "technical
 * fingerprint". Only humans familiar with the use case can distinguish between variants.
 * Operational model may also be a distinguishing attribute (e.g. hyperscaler - AWS, Azure, etc.).
 * Variants all have the same billing model.
 */
@Data
public class Variant {

  @NotNull @NotEmpty private String tag; // required
  private List<String> roles;
  private List<String> engineeringIds;
  private List<String> productNames;

  public Optional<Subscription> getSubscription() throws IOException {
    return SubscriptionConfigRegistry.getInstance().lookupSubscriptionByVariant(tag);
  }

  public static Optional<Variant> findByRole(String role) throws IOException {
    AtomicReference<Optional<Variant>> match = new AtomicReference<>(Optional.empty());

    SubscriptionConfigRegistry.getInstance()
        .findSubscriptionByRole(role)
        .ifPresent(
            subscription -> {
              match.set(
                  subscription.getVariants().stream()
                      .filter(Objects::nonNull)
                      .filter(variant -> !variant.getRoles().isEmpty())
                      .filter(variant -> variant.getRoles().contains(role))
                      .findFirst());
            });

    return match.get();
  }
}
