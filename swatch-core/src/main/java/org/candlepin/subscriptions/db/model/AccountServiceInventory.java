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
package org.candlepin.subscriptions.db.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.CascadeType;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Aggregate for an inventory of service instances for a given account.
 *
 * <p>Using an aggregate simplifies modification, as we can leverage JPA to track persistence of
 * service instances.
 */
@Entity
@Table(name = "account_services")
@Getter
@Setter
public class AccountServiceInventory implements Serializable {
  @EmbeddedId private AccountServiceInventoryId id;

  public AccountServiceInventory() {
    id = new AccountServiceInventoryId();
  }

  public AccountServiceInventory(String accountNumber, String serviceType) {
    id = new AccountServiceInventoryId(accountNumber, serviceType);
  }

  public String getAccountNumber() {
    return id.getAccountNumber();
  }

  public void setAccountNumber(String accountNumber) {
    id.setAccountNumber(accountNumber);
  }

  public String getServiceType() {
    return id.getServiceType();
  }

  public void setServiceType(String serviceType) {
    id.setServiceType(serviceType);
  }

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(
      name = "account_number",
      referencedColumnName = "account_number",
      insertable = false,
      updatable = false)
  @JoinColumn(
      name = "instance_type",
      referencedColumnName = "service_type",
      insertable = false,
      updatable = false)
  // NOTE: insertable = false and updatable=false prevents extraneous update statements (they're
  // handled in hosts table)
  @MapKeyColumn(name = "instance_id", updatable = false, insertable = false)
  private Map<String, Host> serviceInstances = new HashMap<>();
}
