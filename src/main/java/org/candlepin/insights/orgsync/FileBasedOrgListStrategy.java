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
package org.candlepin.insights.orgsync;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

/**
 * An implementation of OrgListStrategy that reads org ids from a file
 */
public class FileBasedOrgListStrategy implements OrgListStrategy, ResourceLoaderAware {
    private ResourceLoader resourceLoader;
    private String orgResourceLocation;
    private Resource orgResource;

    public FileBasedOrgListStrategy(FileBasedOrgListStrategyProperties props) {
        this.orgResourceLocation = props.getOrgResourceLocation();
    }

    @Override
    public List<String> getOrgsToSync() throws IOException {
        // Re-read the file every time.  It shouldn't be a massive file and doing so allows us to update the
        // org list without restarting the app.
        try (InputStream s = orgResource.getInputStream()) {
            return new BufferedReader(new InputStreamReader(s, Charset.defaultCharset()))
                .lines()
                .collect(Collectors.toList());
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            orgResource = resourceLoader.getResource(orgResourceLocation);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The orgResourceLocation property is unset. Please set in " +
                "the application configuration files.");
        }

        if (!orgResource.exists()) {
            throw new IllegalStateException(
                "Cannot find the resource " + orgResource.getDescription()
            );
        }
    }
}
