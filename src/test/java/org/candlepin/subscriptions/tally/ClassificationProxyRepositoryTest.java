/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class ClassificationProxyRepositoryTest {

    @MockBean private InventoryRepository inventoryRepository;
    @MockBean private BuildProperties buildProperties;
    @Autowired private ClassificationProxyRepository proxyRepository;

    @Test
    public void testMarksGuestsWithUnknownHypervisor() throws Exception {
        InventoryHostFacts guest = new InventoryHostFacts();
        guest.setVirtual(true);
        guest.setSubscriptionManagerId("sub-man-id");
        guest.setHypervisorUuid("no-such-hypervisor");

        List<InventoryHostFacts> testFacts = new ArrayList<>();
        testFacts.add(guest);

        when(inventoryRepository.getFacts(any())).thenReturn(testFacts.stream());

        List<ClassifiedInventoryHostFacts> enhancedFacts =
            proxyRepository.getFacts(Arrays.asList("123")).collect(Collectors.toList());

        assertEquals(true, enhancedFacts.get(0).isHypervisorUnknown());
    }

    @Test
    public void testMarksGuestWithUnknownHypervisorWhenHypervisorIdIsNull() {
        InventoryHostFacts guest = new InventoryHostFacts();
        guest.setVirtual(true);
        guest.setSubscriptionManagerId("sub-man-id");
        assertNull(guest.getHypervisorUuid());

        List<InventoryHostFacts> testFacts = new ArrayList<>();
        testFacts.add(guest);

        when(inventoryRepository.getFacts(any())).thenReturn(testFacts.stream());

        List<ClassifiedInventoryHostFacts> enhancedFacts =
            proxyRepository.getFacts(Arrays.asList("123")).collect(Collectors.toList());

        assertEquals(true, enhancedFacts.get(0).isHypervisorUnknown());
    }

    @Test
    public void testMarksHypervisors() throws Exception {
        InventoryHostFacts guest = new InventoryHostFacts();
        guest.setVirtual(true);
        guest.setSubscriptionManagerId("sub-man-id");
        guest.setHypervisorUuid("my-hypervisor");

        InventoryHostFacts hypervisor1 = new InventoryHostFacts();
        hypervisor1.setSubscriptionManagerId("my-hypervisor");

        InventoryHostFacts hypervisor2 = new InventoryHostFacts();
        hypervisor2.setSubscriptionManagerId("another-hypervisor");

        List<InventoryHostFacts> testFacts = Arrays.asList(guest, hypervisor1, hypervisor2);

        when(inventoryRepository.getFacts(any())).thenReturn(testFacts.stream());

        Stream<ClassifiedInventoryHostFacts> enhancedFacts =
            proxyRepository.getFacts(Arrays.asList("123"));

        List<ClassifiedInventoryHostFacts> results = enhancedFacts
            .filter(x -> "my-hypervisor".equals(x.getSubscriptionManagerId()))
            .collect(Collectors.toList());

        assertEquals(1, results.size());
        assertEquals(true, results.get(0).isHypervisor());
    }

}
