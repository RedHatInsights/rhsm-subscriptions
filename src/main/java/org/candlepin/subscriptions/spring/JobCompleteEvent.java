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
package org.candlepin.subscriptions.spring;


import org.springframework.context.ApplicationEvent;

/**
 * Event thrown when a job is completed.  Primarily is so the listener can shutdown the pod after
 * running the job once if the application is deployed to OpenShift as a Cron Job.
 */
public class JobCompleteEvent extends ApplicationEvent {
    /**
     * Create a new JobCompleteEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public JobCompleteEvent(Object source) {
        super(source);
    }
}
