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
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"hardware_measurement_type", "metric_id", "value", "currentTotal"})
public class TallyMeasurement {

  /** (Required) */
  @JsonProperty("hardware_measurement_type")
  @NotNull
  private String hardwareMeasurementType;

  /** Preferred unit of measure for the subject (for products with multiple possible metrics). */
  @JsonProperty("metric_id")
  @JsonPropertyDescription(
      "Preferred unit of measure for the subject (for products with multiple possible metrics).")
  private String metricId;

  /** Measurement value. (Required) */
  @JsonProperty("value")
  @JsonPropertyDescription("Measurement value.")
  @NotNull
  private Double value;

  /** Sum of all measurements between the start of the month to the snapshot date. */
  @JsonProperty("currentTotal")
  @JsonPropertyDescription(
      "Sum of all measurements between the start of the month to the snapshot date.")
  private Double currentTotal;

  /** (Required) */
  @JsonProperty("hardware_measurement_type")
  public String getHardwareMeasurementType() {
    return hardwareMeasurementType;
  }

  /** (Required) */
  @JsonProperty("hardware_measurement_type")
  public void setHardwareMeasurementType(String hardwareMeasurementType) {
    this.hardwareMeasurementType = hardwareMeasurementType;
  }

  public TallyMeasurement withHardwareMeasurementType(String hardwareMeasurementType) {
    this.hardwareMeasurementType = hardwareMeasurementType;
    return this;
  }

  /** Preferred unit of measure for the subject (for products with multiple possible metrics). */
  @JsonProperty("metric_id")
  public String getMetricId() {
    return metricId;
  }

  /** Preferred unit of measure for the subject (for products with multiple possible metrics). */
  @JsonProperty("metric_id")
  public void setMetricId(String metricId) {
    this.metricId = metricId;
  }

  public TallyMeasurement withMetricId(String metricId) {
    this.metricId = metricId;
    return this;
  }

  /** Measurement value. (Required) */
  @JsonProperty("value")
  public Double getValue() {
    return value;
  }

  /** Measurement value. (Required) */
  @JsonProperty("value")
  public void setValue(Double value) {
    this.value = value;
  }

  public TallyMeasurement withValue(Double value) {
    this.value = value;
    return this;
  }

  /** Sum of all measurements between the start of the month to the snapshot date. */
  @JsonProperty("currentTotal")
  public Double getCurrentTotal() {
    return currentTotal;
  }

  /** Sum of all measurements between the start of the month to the snapshot date. */
  @JsonProperty("currentTotal")
  public void setCurrentTotal(Double currentTotal) {
    this.currentTotal = currentTotal;
  }

  public TallyMeasurement withCurrentTotal(Double currentTotal) {
    this.currentTotal = currentTotal;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(TallyMeasurement.class.getName())
        .append('@')
        .append(Integer.toHexString(System.identityHashCode(this)))
        .append('[');
    sb.append("hardwareMeasurementType");
    sb.append('=');
    sb.append(((this.hardwareMeasurementType == null) ? "<null>" : this.hardwareMeasurementType));
    sb.append(',');
    sb.append("metricId");
    sb.append('=');
    sb.append(((this.metricId == null) ? "<null>" : this.metricId));
    sb.append(',');
    sb.append("value");
    sb.append('=');
    sb.append(((this.value == null) ? "<null>" : this.value));
    sb.append(',');
    sb.append("currentTotal");
    sb.append('=');
    sb.append(((this.currentTotal == null) ? "<null>" : this.currentTotal));
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
    result = ((result * 31) + ((this.currentTotal == null) ? 0 : this.currentTotal.hashCode()));
    result = ((result * 31) + ((this.metricId == null) ? 0 : this.metricId.hashCode()));
    result =
        ((result * 31)
            + ((this.hardwareMeasurementType == null)
                ? 0
                : this.hardwareMeasurementType.hashCode()));
    result = ((result * 31) + ((this.value == null) ? 0 : this.value.hashCode()));
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof TallyMeasurement)) {
      return false;
    }
    TallyMeasurement rhs = ((TallyMeasurement) other);
    return ((((Objects.equals(this.currentTotal, rhs.currentTotal))
                && (Objects.equals(this.metricId, rhs.metricId)))
            && (Objects.equals(this.hardwareMeasurementType, rhs.hardwareMeasurementType)))
        && (Objects.equals(this.value, rhs.value)));
  }
}
