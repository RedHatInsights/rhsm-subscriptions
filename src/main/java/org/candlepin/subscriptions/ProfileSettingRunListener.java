/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;

/** Listener that sets a pre-requisite profile once the environment is prepared.
 *
 * The CaptureSnapshotsJob requires a TaskManager to run. TaskManagers are only enabled with the
 * "in-memory-queue", "kafka-queue", or "worker" profiles. If we try to create a CaptureSnapshotsJob without
 * a TaskManager-enabling profile set, the application will fail to start.  Since we know that the
 * capture-snapshots profile requires a TaskManager-enabling profile, we'll set it here to
 * in-memory-queue if the user forgot to set it.
 */
// Not a component because this listener is invoked before the context is even created!
public class ProfileSettingRunListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ProfileSettingRunListener.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        List<String> profiles = Arrays.asList(env.getActiveProfiles());

        boolean isJobProfile = profiles.contains("capture-snapshots") || profiles.contains("purge-snapshots");
        boolean isQueueEnabled = profiles.contains("kafka-queue") || profiles.contains("in-memory-queue");

        if (isJobProfile && !isQueueEnabled) {
            log.warn("Snapshots profiles require a queue profile to be enabled.  Enabling in-memory-queue " +
                "as a fallback");
            env.addActiveProfile("in-memory-queue");
        }
    }
}
