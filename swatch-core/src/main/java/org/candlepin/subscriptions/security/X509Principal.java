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
package org.candlepin.subscriptions.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Represents a user or service account authenticated using mTLS. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class X509Principal implements RhIdentity.Identity {

  /** Container for x509 properties in the x-rh-identity JSON object. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class X509Properties {
    @JsonProperty("subject_dn")
    private String subjectDn;

    public String getSubjectDn() {
      return subjectDn;
    }

    public void setSubjectDn(String subjectDn) {
      this.subjectDn = subjectDn;
    }
  }

  @JsonProperty("x509")
  private X509Properties x509Properties;

  public X509Properties getX509Properties() {
    return x509Properties;
  }

  public void setX509Properties(X509Properties x509Properties) {
    this.x509Properties = x509Properties;
  }

  public String getSubjectDn() {
    return getX509Properties().getSubjectDn();
  }

  @Override
  public String toString() {
    return "X509Principal{" + "subjectDn='" + getSubjectDn() + '\'' + '}';
  }
}
