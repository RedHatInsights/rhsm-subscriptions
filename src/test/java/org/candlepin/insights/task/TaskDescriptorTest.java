/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.task;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class TaskDescriptorTest {

    @Test
    public void testCreation() {
        String expectedGroupId = "my-group";
        TaskType expectedTaskType = TaskType.UPDATE_ORG_INVENTORY;
        String expectedArgKey = "arg1";
        String expectedArgValue = "arg1-val";

        TaskDescriptor desc = TaskDescriptor.builder(expectedTaskType, expectedGroupId)
            .setArg(expectedArgKey, expectedArgValue)
            .build();

        assertEquals(expectedGroupId, desc.getGroupId());
        assertEquals(expectedTaskType, desc.getTaskType());

        Map<String, String> args = desc.getTaskArgs();
        assertTrue(args.containsKey(expectedArgKey));
        assertEquals(expectedArgValue, args.get(expectedArgKey));
        assertEquals(expectedArgValue, desc.getArg(expectedArgKey));
    }

    @Test
    public void testEquality() {
        TaskDescriptor d1 = TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group1")
            .setArg("a1", "a1v")
            .build();

        TaskDescriptor d1Copy = TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group1")
            .setArg("a1", "a1v")
            .build();
        assertEquals(d1, d1Copy);

        TaskDescriptor differentGroup =
            TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group2").build();
        assertNotEquals(d1, differentGroup);

        TaskDescriptor nullGroup = TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, null).build();
        assertNotEquals(d1, nullGroup);

        TaskDescriptor nullType = TaskDescriptor.builder(null, "group1").build();
        assertNotEquals(d1, nullGroup);

        TaskDescriptor argsNotEqual = TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group1").build();
        assertNotEquals(d1, argsNotEqual);

        TaskDescriptor argValueNotEqual = TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group1")
            .setArg("a1", "a1v_")
            .build();
        assertNotEquals(d1, argValueNotEqual);

        TaskDescriptor differentArgs = TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "group1")
            .setArg("a2", "v2")
            .build();
        assertNotEquals(d1, differentArgs);
    }

}
