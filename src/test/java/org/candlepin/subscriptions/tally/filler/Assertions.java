/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally.filler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.subscriptions.utilization.api.model.TallySnapshot;

import java.time.OffsetDateTime;

public class Assertions {

    private Assertions() {
        throw new IllegalStateException("Utility class; should never be instantiated!");
    }

    /**
     * Assert the state of a given snapshot.
     *
     * @param snap the snapshot under test
     * @param date the expected snapshot date
     * @param cores the expected snapshot cores
     * @param sockets the expected snapshot sockets
     * @param instanceCount the expected snapshot instance count
     * @param hasData whether the snapshot was expected to have been generated.
     */
    public static void assertSnapshot(TallySnapshot snap, OffsetDateTime date, Integer cores, Integer sockets,
        Integer instanceCount, Boolean hasData) {
        assertEquals(date, snap.getDate(), "Invalid snapshot date");
        assertEquals(cores, snap.getCores(), "Invalid snapshot cores");
        assertEquals(sockets, snap.getSockets(), "Invalid snapshot sockets");
        assertEquals(instanceCount, snap.getInstanceCount(), "Invalid snapshot instance count");
        assertEquals(hasData, snap.getHasData());
    }
}
