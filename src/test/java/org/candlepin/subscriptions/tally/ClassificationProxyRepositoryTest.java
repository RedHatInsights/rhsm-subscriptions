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

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class ClassificationProxyRepositoryTest {

    private InventoryRepository inventoryRepository;
    private ClassificationProxyRepository proxyRepository;
    private ApplicationClock clock;

    public ClassificationProxyRepositoryTest() {
        inventoryRepository = mock(InventoryRepository.class);
        clock = new FixedClockConfiguration().fixedClock();
        proxyRepository = new ClassificationProxyRepository(inventoryRepository, clock,
            new ApplicationProperties());
    }

    @Test
    public void testMarksGuestsWithUnknownHypervisor() throws Exception {
        InventoryHostFacts guest = new InventoryHostFacts();
        guest.setVirtual(true);
        guest.setSubscriptionManagerId("sub-man-id");
        guest.setHypervisorUuid("no-such-hypervisor");

        List<InventoryHostFacts> testFacts = new ArrayList<>();
        testFacts.add(guest);

        when(inventoryRepository.getFacts(any(), anyInt())).thenReturn(testFacts.stream());

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

        when(inventoryRepository.getFacts(any(), anyInt())).thenReturn(testFacts.stream());

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

        when(inventoryRepository.getFacts(any(), anyInt())).thenReturn(testFacts.stream());

        Stream<ClassifiedInventoryHostFacts> enhancedFacts =
            proxyRepository.getFacts(Arrays.asList("123"));

        List<ClassifiedInventoryHostFacts> results = enhancedFacts
            .filter(x -> "my-hypervisor".equals(x.getSubscriptionManagerId()))
            .collect(Collectors.toList());

        assertEquals(1, results.size());
        assertEquals(true, results.get(0).isHypervisor());
    }

    @Test
    public void ensureStaleHostsAreNotIncludedInFacts() {
        InventoryHostFacts system = new InventoryHostFacts();
        system.setSubscriptionManagerId("valid-system-1");
        system.setStaleTimestamp(clock.endOfCurrentMonth());

        // System included if we can not check stale date due to null date.
        InventoryHostFacts systemWithUnknownStaleDate = new InventoryHostFacts();
        systemWithUnknownStaleDate.setSubscriptionManagerId("valid-system-2");
        systemWithUnknownStaleDate.setStaleTimestamp(null);

        InventoryHostFacts staleSystem1 = new InventoryHostFacts();
        staleSystem1.setSubscriptionManagerId("stale-1");
        staleSystem1.setStaleTimestamp(clock.startOfCurrentMonth().minusMonths(4));

        InventoryHostFacts staleSystem2 = new InventoryHostFacts();
        staleSystem2.setSubscriptionManagerId("stale-2");
        staleSystem2.setStaleTimestamp(clock.startOfCurrentMonth().minusMonths(10));

        List<InventoryHostFacts> facts = Arrays.asList(
            system,
            staleSystem1,
            staleSystem2,
            systemWithUnknownStaleDate
        );

        when(inventoryRepository.getFacts(any(), anyInt())).thenReturn(facts.stream());

        Map<String, ClassifiedInventoryHostFacts> enhancedFacts =
            proxyRepository.getFacts(Arrays.asList("123"))
            .collect(Collectors.toMap(ClassifiedInventoryHostFacts::getSubscriptionManagerId,
            Function.identity()));
        assertEquals(2, enhancedFacts.size());
        assertTrue(enhancedFacts.keySet().containsAll(Arrays.asList("valid-system-1", "valid-system-2")));
        assertEquals("valid-system-1", enhancedFacts.get("valid-system-1").getSubscriptionManagerId());
        assertEquals("valid-system-2", enhancedFacts.get("valid-system-2").getSubscriptionManagerId());

    }

}
