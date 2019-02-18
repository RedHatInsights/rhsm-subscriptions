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

package org.candlepin.insights.pinhead.client;

import java.io.IOException;
import java.io.InputStream;

import javax.net.ssl.HostnameVerifier;

/**
 * Class to hold values used to build the ApiClient instance wrapped in an SSLContext for Pinhead.
 */
public class PinheadApiConfiguration {
    private final X509ApiClientFactoryConfiguration x509Config = new X509ApiClientFactoryConfiguration();
    private boolean useStub;
    private String url;

    public boolean isUseStub() {
        return useStub;
    }

    public void setUseStub(boolean useStub) {
        this.useStub = useStub;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public X509ApiClientFactoryConfiguration getX509ApiClientFactoryConfiguration() {
        return x509Config;
    }

    public String getKeystorePassword() {
        return x509Config.getKeystorePassword();
    }

    public void setKeystorePassword(String keystorePassword) {
        x509Config.setKeystorePassword(keystorePassword);
    }

    public String getTruststorePassword() {
        return x509Config.getTruststorePassword();
    }

    public void setTruststorePassword(String truststorePassword) {
        x509Config.setTruststorePassword(truststorePassword);
    }

    public String getKeystoreFile() {
        return x509Config.getKeystoreFile();
    }

    public void setKeystoreFile(String keystoreFile) {
        x509Config.setKeystoreFile(keystoreFile);
    }

    public String getTruststoreFile() {
        return x509Config.getTruststoreFile();
    }

    public void setTruststoreFile(String truststoreFile) {
        x509Config.setTruststoreFile(truststoreFile);
    }

    public InputStream getKeystoreStream() throws IOException {
        return x509Config.getKeystoreStream();
    }

    public InputStream getTruststoreStream() throws IOException {
        return x509Config.getTruststoreStream();
    }

    public HostnameVerifier getHostnameVerifier() {
        return x509Config.getHostnameVerifier();
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        x509Config.setHostnameVerifier(hostnameVerifier);
    }

    public boolean usesClientAuth() {
        return x509Config.usesClientAuth();
    }
}
