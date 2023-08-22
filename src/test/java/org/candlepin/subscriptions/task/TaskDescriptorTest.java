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
package org.candlepin.subscriptions.task;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class TaskDescriptorTest {

  @Test
  void testCreation() {
    String expectedGroupId = "my-group";
    TaskType expectedTaskType = TaskType.UPDATE_SNAPSHOTS;
    String expectedArgKey = "arg1";
    List<String> expectedArgValues = Arrays.asList("1", "2", "3");

    TaskDescriptor desc =
        TaskDescriptor.builder(expectedTaskType, expectedGroupId, null)
            .setArg(expectedArgKey, expectedArgValues)
            .build();

    assertEquals(expectedGroupId, desc.getGroupId());
    assertEquals(expectedTaskType, desc.getTaskType());

    Map<String, List<String>> args = desc.getTaskArgs();
    assertThat(args, Matchers.hasKey(expectedArgKey));
    assertEquals(expectedArgValues, args.get(expectedArgKey));
    assertEquals(expectedArgValues, desc.getArg(expectedArgKey));
  }

  @Test
  void testEquality() {
    TaskDescriptor d1 =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group1", null)
            .setSingleValuedArg("a1", "a1v")
            .build();

    TaskDescriptor d1Copy =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group1", null)
            .setSingleValuedArg("a1", "a1v")
            .build();
    assertEquals(d1, d1Copy);

    TaskDescriptor differentGroup =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group2", null).build();
    assertNotEquals(d1, differentGroup);

    TaskDescriptor nullGroup =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, null, null).build();
    assertNotEquals(d1, nullGroup);

    TaskDescriptor nullType = TaskDescriptor.builder(null, "group1", null).build();
    assertNotEquals(d1, nullType);

    TaskDescriptor argsNotEqual =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group1", null).build();
    assertNotEquals(d1, argsNotEqual);

    TaskDescriptor argValueNotEqual =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group1", null)
            .setSingleValuedArg("a1", "a1v_")
            .build();
    assertNotEquals(d1, argValueNotEqual);

    TaskDescriptor differentArgs =
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, "group1", null)
            .setSingleValuedArg("a2", "v2")
            .build();
    assertNotEquals(d1, differentArgs);
  }
}
