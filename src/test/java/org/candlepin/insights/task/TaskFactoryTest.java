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
package org.candlepin.insights.task;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.insights.task.tasks.UpdateOrgInventoryTask;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class TaskFactoryTest {

    @Autowired
    private TaskFactory factory;

    @Test
    public void ensureFactoryBuildsUpdateOrgInventoryTask() {
        Task task = factory.build(TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, "my-group").build());
        assertThat(task, Matchers.instanceOf(UpdateOrgInventoryTask.class));
    }

    @Test
    public void ensureIllegalArgumentExceptionWhenTaskTypeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            factory.build(TaskDescriptor.builder(null, "my-group").build());
        });
    }
}
