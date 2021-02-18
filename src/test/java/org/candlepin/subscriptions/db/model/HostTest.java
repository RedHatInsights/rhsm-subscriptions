package org.candlepin.subscriptions.db.model;

import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.Test;

public class HostTest {

    @Test
    void populateFieldsFromHbi () {
        final Host host = new Host();

        InventoryHostFacts inventoryHostFacts = new InventoryHostFacts();
        NormalizedFacts normalizedFacts = new NormalizedFacts();



    }
}
