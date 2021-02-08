/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import org.candlepin.subscriptions.json.Measurement;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Model object to the key for a given tally measurement
 */
@Embeddable
public class TallyMeasurementKey implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type")
    private HardwareMeasurementType measurementType;

    @Enumerated(EnumType.STRING)
    private Measurement.Uom uom;

    public TallyMeasurementKey() {
        /* intentionally left empty */
    }

    public TallyMeasurementKey(HardwareMeasurementType hardwareMeasurementType, Measurement.Uom uom) {
        this.measurementType = hardwareMeasurementType;
        this.uom = uom;
    }

    public HardwareMeasurementType getMeasurementType() {
        return measurementType;
    }

    public void setMeasurementType(HardwareMeasurementType measurementType) {
        this.measurementType = measurementType;
    }

    public Measurement.Uom getUom() {
        return uom;
    }

    public void setUom(Measurement.Uom uom) {
        this.uom = uom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TallyMeasurementKey)) {
            return false;
        }
        TallyMeasurementKey that = (TallyMeasurementKey) o;
        return measurementType == that.measurementType && uom == that.uom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(measurementType, uom);
    }

    @Override
    public String toString() {
        return "TallyMeasurementKey{" + "measurementType=" + measurementType + ", uom=" + uom + '}';
    }
}
