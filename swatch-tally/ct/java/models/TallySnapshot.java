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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "billing_provider",
  "billing_account_id",
  "snapshot_date",
  "product_id",
  "sla",
  "usage",
  "granularity",
  "tally_measurements"
})
public class TallySnapshot {

  @JsonProperty("id")
  private UUID id;

  @JsonProperty("billing_provider")
  private TallySnapshot.BillingProvider billingProvider;

  @JsonProperty("billing_account_id")
  private String billingAccountId;

  @JsonProperty("snapshot_date")
  private OffsetDateTime snapshotDate;

  @JsonProperty("product_id")
  private String productId;

  /** Service level for the subject. */
  @JsonProperty("sla")
  @JsonPropertyDescription("Service level for the subject.")
  private TallySnapshot.Sla sla;

  /** Intended usage for the subject. */
  @JsonProperty("usage")
  @JsonPropertyDescription("Intended usage for the subject.")
  private TallySnapshot.Usage usage;

  @JsonProperty("granularity")
  private TallySnapshot.Granularity granularity;

  @JsonProperty("tally_measurements")
  @Valid
  private List<TallyMeasurement> tallyMeasurements;

  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  @JsonProperty("id")
  public void setId(UUID id) {
    this.id = id;
  }

  public TallySnapshot withId(UUID id) {
    this.id = id;
    return this;
  }

  @JsonProperty("billing_provider")
  public TallySnapshot.BillingProvider getBillingProvider() {
    return billingProvider;
  }

  @JsonProperty("billing_provider")
  public void setBillingProvider(TallySnapshot.BillingProvider billingProvider) {
    this.billingProvider = billingProvider;
  }

  public TallySnapshot withBillingProvider(TallySnapshot.BillingProvider billingProvider) {
    this.billingProvider = billingProvider;
    return this;
  }

  @JsonProperty("billing_account_id")
  public String getBillingAccountId() {
    return billingAccountId;
  }

  @JsonProperty("billing_account_id")
  public void setBillingAccountId(String billingAccountId) {
    this.billingAccountId = billingAccountId;
  }

  public TallySnapshot withBillingAccountId(String billingAccountId) {
    this.billingAccountId = billingAccountId;
    return this;
  }

  @JsonProperty("snapshot_date")
  public OffsetDateTime getSnapshotDate() {
    return snapshotDate;
  }

  @JsonProperty("snapshot_date")
  public void setSnapshotDate(OffsetDateTime snapshotDate) {
    this.snapshotDate = snapshotDate;
  }

  public TallySnapshot withSnapshotDate(OffsetDateTime snapshotDate) {
    this.snapshotDate = snapshotDate;
    return this;
  }

  @JsonProperty("product_id")
  public String getProductId() {
    return productId;
  }

  @JsonProperty("product_id")
  public void setProductId(String productId) {
    this.productId = productId;
  }

  public TallySnapshot withProductId(String productId) {
    this.productId = productId;
    return this;
  }

  /** Service level for the subject. */
  @JsonProperty("sla")
  public TallySnapshot.Sla getSla() {
    return sla;
  }

  /** Service level for the subject. */
  @JsonProperty("sla")
  public void setSla(TallySnapshot.Sla sla) {
    this.sla = sla;
  }

  public TallySnapshot withSla(TallySnapshot.Sla sla) {
    this.sla = sla;
    return this;
  }

  /** Intended usage for the subject. */
  @JsonProperty("usage")
  public TallySnapshot.Usage getUsage() {
    return usage;
  }

  /** Intended usage for the subject. */
  @JsonProperty("usage")
  public void setUsage(TallySnapshot.Usage usage) {
    this.usage = usage;
  }

  public TallySnapshot withUsage(TallySnapshot.Usage usage) {
    this.usage = usage;
    return this;
  }

  @JsonProperty("granularity")
  public TallySnapshot.Granularity getGranularity() {
    return granularity;
  }

  @JsonProperty("granularity")
  public void setGranularity(TallySnapshot.Granularity granularity) {
    this.granularity = granularity;
  }

  public TallySnapshot withGranularity(TallySnapshot.Granularity granularity) {
    this.granularity = granularity;
    return this;
  }

  @JsonProperty("tally_measurements")
  public List<TallyMeasurement> getTallyMeasurements() {
    return tallyMeasurements;
  }

  @JsonProperty("tally_measurements")
  public void setTallyMeasurements(List<TallyMeasurement> tallyMeasurements) {
    this.tallyMeasurements = tallyMeasurements;
  }

  public TallySnapshot withTallyMeasurements(List<TallyMeasurement> tallyMeasurements) {
    this.tallyMeasurements = tallyMeasurements;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(TallySnapshot.class.getName())
        .append('@')
        .append(Integer.toHexString(System.identityHashCode(this)))
        .append('[');
    sb.append("id");
    sb.append('=');
    sb.append(((this.id == null) ? "<null>" : this.id));
    sb.append(',');
    sb.append("billingProvider");
    sb.append('=');
    sb.append(((this.billingProvider == null) ? "<null>" : this.billingProvider));
    sb.append(',');
    sb.append("billingAccountId");
    sb.append('=');
    sb.append(((this.billingAccountId == null) ? "<null>" : this.billingAccountId));
    sb.append(',');
    sb.append("snapshotDate");
    sb.append('=');
    sb.append(((this.snapshotDate == null) ? "<null>" : this.snapshotDate));
    sb.append(',');
    sb.append("productId");
    sb.append('=');
    sb.append(((this.productId == null) ? "<null>" : this.productId));
    sb.append(',');
    sb.append("sla");
    sb.append('=');
    sb.append(((this.sla == null) ? "<null>" : this.sla));
    sb.append(',');
    sb.append("usage");
    sb.append('=');
    sb.append(((this.usage == null) ? "<null>" : this.usage));
    sb.append(',');
    sb.append("granularity");
    sb.append('=');
    sb.append(((this.granularity == null) ? "<null>" : this.granularity));
    sb.append(',');
    sb.append("tallyMeasurements");
    sb.append('=');
    sb.append(((this.tallyMeasurements == null) ? "<null>" : this.tallyMeasurements));
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
    result = ((result * 31) + ((this.snapshotDate == null) ? 0 : this.snapshotDate.hashCode()));
    result =
        ((result * 31)
            + ((this.tallyMeasurements == null) ? 0 : this.tallyMeasurements.hashCode()));
    result = ((result * 31) + ((this.productId == null) ? 0 : this.productId.hashCode()));
    result = ((result * 31) + ((this.granularity == null) ? 0 : this.granularity.hashCode()));
    result = ((result * 31) + ((this.usage == null) ? 0 : this.usage.hashCode()));
    result = ((result * 31) + ((this.sla == null) ? 0 : this.sla.hashCode()));
    result =
        ((result * 31) + ((this.billingAccountId == null) ? 0 : this.billingAccountId.hashCode()));
    result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
    result =
        ((result * 31) + ((this.billingProvider == null) ? 0 : this.billingProvider.hashCode()));
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof TallySnapshot)) {
      return false;
    }
    TallySnapshot rhs = ((TallySnapshot) other);
    return (((((((((Objects.equals(this.snapshotDate, rhs.snapshotDate))
                                    && (Objects.equals(
                                        this.tallyMeasurements, rhs.tallyMeasurements)))
                                && (Objects.equals(this.productId, rhs.productId)))
                            && (Objects.equals(this.granularity, rhs.granularity)))
                        && (Objects.equals(this.usage, rhs.usage)))
                    && (Objects.equals(this.sla, rhs.sla)))
                && (Objects.equals(this.billingAccountId, rhs.billingAccountId)))
            && (Objects.equals(this.id, rhs.id)))
        && (Objects.equals(this.billingProvider, rhs.billingProvider)));
  }

  public enum BillingProvider {
    __EMPTY__(""),
    RED_HAT("red hat"),
    AWS("aws"),
    GCP("gcp"),
    AZURE("azure"),
    ORACLE("oracle"),
    ANY("_ANY");
    private final String value;
    private static final Map<String, TallySnapshot.BillingProvider> CONSTANTS =
        new HashMap<String, TallySnapshot.BillingProvider>();

    static {
      for (TallySnapshot.BillingProvider c : values()) {
        CONSTANTS.put(c.value, c);
      }
    }

    BillingProvider(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }

    @JsonValue
    public String value() {
      return this.value;
    }

    @JsonCreator
    public static TallySnapshot.BillingProvider fromValue(String value) {
      TallySnapshot.BillingProvider constant = CONSTANTS.get(value);
      if (constant == null) {
        throw new IllegalArgumentException(value);
      } else {
        return constant;
      }
    }
  }

  public enum Granularity {
    HOURLY("Hourly"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly");
    private final String value;
    private static final Map<String, TallySnapshot.Granularity> CONSTANTS =
        new HashMap<String, TallySnapshot.Granularity>();

    static {
      for (TallySnapshot.Granularity c : values()) {
        CONSTANTS.put(c.value, c);
      }
    }

    Granularity(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }

    @JsonValue
    public String value() {
      return this.value;
    }

    @JsonCreator
    public static TallySnapshot.Granularity fromValue(String value) {
      TallySnapshot.Granularity constant = CONSTANTS.get(value);
      if (constant == null) {
        throw new IllegalArgumentException(value);
      } else {
        return constant;
      }
    }
  }

  /** Service level for the subject. */
  public enum Sla {
    __EMPTY__(""),
    PREMIUM("Premium"),
    STANDARD("Standard"),
    SELF_SUPPORT("Self-Support"),
    ANY("_ANY");
    private final String value;
    private static final Map<String, TallySnapshot.Sla> CONSTANTS =
        new HashMap<String, TallySnapshot.Sla>();

    static {
      for (TallySnapshot.Sla c : values()) {
        CONSTANTS.put(c.value, c);
      }
    }

    Sla(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }

    @JsonValue
    public String value() {
      return this.value;
    }

    @JsonCreator
    public static TallySnapshot.Sla fromValue(String value) {
      TallySnapshot.Sla constant = CONSTANTS.get(value);
      if (constant == null) {
        throw new IllegalArgumentException(value);
      } else {
        return constant;
      }
    }
  }

  /** Intended usage for the subject. */
  public enum Usage {
    __EMPTY__(""),
    PRODUCTION("Production"),
    DEVELOPMENT_TEST("Development/Test"),
    DISASTER_RECOVERY("Disaster Recovery"),
    ANY("_ANY");
    private final String value;
    private static final Map<String, TallySnapshot.Usage> CONSTANTS =
        new HashMap<String, TallySnapshot.Usage>();

    static {
      for (TallySnapshot.Usage c : values()) {
        CONSTANTS.put(c.value, c);
      }
    }

    Usage(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }

    @JsonValue
    public String value() {
      return this.value;
    }

    @JsonCreator
    public static TallySnapshot.Usage fromValue(String value) {
      TallySnapshot.Usage constant = CONSTANTS.get(value);
      if (constant == null) {
        throw new IllegalArgumentException(value);
      } else {
        return constant;
      }
    }
  }
}
