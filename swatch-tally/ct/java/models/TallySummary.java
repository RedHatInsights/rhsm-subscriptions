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
package models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"org_id", "tally_snapshots"})
public class TallySummary {

  /** The identifier for the relevant organization. (Required) */
  @JsonProperty("org_id")
  @JsonPropertyDescription("The identifier for the relevant organization.")
  @NotNull
  private String orgId;

  /** List of tally snapshots produced in the range. */
  @JsonProperty("tally_snapshots")
  @JsonPropertyDescription("List of tally snapshots produced in the range.")
  @Valid
  private List<TallySnapshot> tallySnapshots;

  /** The identifier for the relevant organization. (Required) */
  @JsonProperty("org_id")
  public String getOrgId() {
    return orgId;
  }

  /** The identifier for the relevant organization. (Required) */
  @JsonProperty("org_id")
  public void setOrgId(String orgId) {
    this.orgId = orgId;
  }

  public TallySummary withOrgId(String orgId) {
    this.orgId = orgId;
    return this;
  }

  /** List of tally snapshots produced in the range. */
  @JsonProperty("tally_snapshots")
  public List<TallySnapshot> getTallySnapshots() {
    return tallySnapshots;
  }

  /** List of tally snapshots produced in the range. */
  @JsonProperty("tally_snapshots")
  public void setTallySnapshots(List<TallySnapshot> tallySnapshots) {
    this.tallySnapshots = tallySnapshots;
  }

  public TallySummary withTallySnapshots(List<TallySnapshot> tallySnapshots) {
    this.tallySnapshots = tallySnapshots;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(TallySummary.class.getName())
        .append('@')
        .append(Integer.toHexString(System.identityHashCode(this)))
        .append('[');
    sb.append("orgId");
    sb.append('=');
    sb.append(((this.orgId == null) ? "<null>" : this.orgId));
    sb.append(',');
    sb.append("tallySnapshots");
    sb.append('=');
    sb.append(((this.tallySnapshots == null) ? "<null>" : this.tallySnapshots));
    sb.append(',');
    if (sb.charAt((sb.length() - 1)) == ',') {
      sb.setCharAt((sb.length() - 1), ']');
    } else {
      sb.append(']');
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = ((result * 31) + ((this.tallySnapshots == null) ? 0 : this.tallySnapshots.hashCode()));
    result = ((result * 31) + ((this.orgId == null) ? 0 : this.orgId.hashCode()));
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof TallySummary)) {
      return false;
    }
    TallySummary rhs = ((TallySummary) other);
    return ((Objects.equals(this.tallySnapshots, rhs.tallySnapshots))
        && (Objects.equals(this.orgId, rhs.orgId)));
  }
}
