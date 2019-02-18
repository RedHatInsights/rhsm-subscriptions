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

import org.apache.http.conn.ssl.DefaultHostnameVerifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.net.ssl.HostnameVerifier;

/**
 * Class to hold values used to build the ApiClient instance wrapped in an SSLContext.
 */
public class X509ApiClientFactoryConfiguration {
    private String keystorePassword;
    private String keystoreFile;
    private String truststoreFile;
    private String truststorePassword;

    private HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
    }

    public String getKeystoreFile() {
        return keystoreFile;
    }

    public void setKeystoreFile(String keystoreFile) {
        this.keystoreFile = keystoreFile;
    }

    public String getTruststoreFile() {
        return truststoreFile;
    }

    public void setTruststoreFile(String truststoreFile) {
        this.truststoreFile = truststoreFile;
    }

    public InputStream getKeystoreStream() throws IOException {
        if (keystoreFile == null) {
            throw new IllegalStateException("No keystore file has been set");
        }
        return readStream(keystoreFile);
    }

    public InputStream getTruststoreStream() throws IOException {
        if (truststoreFile == null) {
            throw new IllegalStateException("No truststore file has been set");
        }
        return readStream(truststoreFile);
    }

    private InputStream readStream(String path) throws IOException {
        return new ByteArrayInputStream(Files.readAllBytes(Paths.get(path)));
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * Allow setting the HostnameVerifier implementation.  NoopHostnameVerifier could be used in testing, for
     * example
     */
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public boolean usesClientAuth() {
        return (getKeystoreFile() != null && getKeystorePassword() != null);
    }

    public String toString() {
        return String.format("X509ApiClientFactoryConfiguration[truststore=%s, keystore=%s]",
            truststoreFile, keystoreFile);
    }

}
