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
package org.candlepin.subscriptions;

import org.candlepin.subscriptions.retention.TallyRetentionPolicyProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * POJO to hold property values via Spring's "Type-Safe Configuration Properties" pattern
 *
 * NB: This class must be labeled as a component, not loaded via the EnableConfigurationProperties annotation.
 * Loading this class as a component gives the bean a formal name.  Using EnableConfigurationProperties
 * uses a generated name with a hyphen in it.  From the Spring Boot Docs (section 4.2.8 Type-safe
 * Configuration):
 *
 *     When the @ConfigurationProperties bean is registered using configuration property scanning or via
 *     @EnableConfigurationProperties, the bean has a conventional name: <prefix>-<fqn>, where <prefix> is
 *     the environment key prefix specified in the @ConfigurationProperties annotation and <fqn> is the fully
 *     qualified name of the bean. If the annotation does not provide any prefix, only the fully qualified
 *     name of the bean is used.
 *
 * Unfortunately, "<prefix>-<fqn>" has a hyphen in it which means we can't use the bean name in a SpEL
 * expression: the hyphen is interpreted as a subtraction operator.
 */
@Component
@ConfigurationProperties(prefix = "rhsm-subscriptions")
public class ApplicationProperties {

    private boolean prettyPrintJson = false;

    private boolean devMode = false;

    private boolean orgAdminOptional = true;

    private final TallyRetentionPolicyProperties tallyRetentionPolicy = new TallyRetentionPolicyProperties();

    /**
     * Resource location of a file containing a mapping of product IDs to product IDs that identify them.
     */
    private String productIdToProductsMapResourceLocation;

    /**
     * Resource location of a file containing a mapping of syspurpose roles to products.
     */
    private String roleToProductsMapResourceLocation;

    /**
     * Resource location of a file containing a list of accounts to process.
     */
    private String accountListResourceLocation;

    /**
     * Resource location of a file containing a list of products (SKUs) to process. If not specified, all
     * products will be processed.
     */
    private String productWhitelistResourceLocation;

    /**
     * Resource location of a file containing the whitelisted accounts allowed to run reports.
     */
    private String reportingAccountWhitelistResourceLocation;

    /**
     * An hour based threshold used to determine whether an inventory host record's rhsm facts are outdated.
     * The host's rhsm.SYNC_TIMESTAMP fact is checked against this threshold. The default is 24 hours.
     */
    private int hostLastSyncThresholdHours = 24;

    /**
     * The batch size of account numbers that will be processed at a time while producing snapshots.
     * Default: 500
     */
    private int accountBatchSize = 500;

    /**
     * Amount of time to cache the account list, before allowing a re-read from the filesystem.
     */
    private Duration accountListCacheTtl = Duration.ofMinutes(5);

    /**
     * Amount of time to cache the product mapping, before allowing a re-read from the filesystem.
     */
    private Duration productIdToProductsMapCacheTtl = Duration.ofMinutes(5);

    /**
     * Amount of time to cache the product whitelist, before allowing a re-read from the filesystem.
     */
    private Duration productWhiteListCacheTtl = Duration.ofMinutes(5);

    /**
     * Amount of time to cache the syspurpose role to products map, before allowing a re-read from the
     * filesystem.
     */
    private Duration roleToProductsMapCacheTtl = Duration.ofMinutes(5);

    /**
     * Amount of time to cache the API access whitelist, before allowing a re-read from the filesystem.
     */
    private Duration reportingAccountWhitelistCacheTtl = Duration.ofMinutes(5);

    /**
     * The number of days after the inventory's stale_timestamp that the record will be culled.
     * Currently HBI is calculating this value and setting it on messages. Right now the
     * default is: stale_timestamp + 14 days. Adding this as a configuration setting since
     * we may need to adjust it at some point to match.
     */
    private int cullingOffsetDays = 14;

    /**
     * Expected domain suffix for origin or referer headers.
     *
     * @see org.candlepin.subscriptions.security.AntiCsrfFilter
     */
    private String antiCsrfDomainSuffix = ".redhat.com";

    /**
     * Expected port for origin or referer headers.
     *
     * @see org.candlepin.subscriptions.security.AntiCsrfFilter
     */
    private int antiCsrfPort = 443;

    /**
     * The RBAC application name that defines the permissions for this application.
     */
    private String rbacApplicationName = "subscriptions";

    public boolean isPrettyPrintJson() {
        return prettyPrintJson;
    }

    public void setPrettyPrintJson(boolean prettyPrintJson) {
        this.prettyPrintJson = prettyPrintJson;
    }

    public String getProductIdToProductsMapResourceLocation() {
        return productIdToProductsMapResourceLocation;
    }

    public void setProductIdToProductsMapResourceLocation(String productIdToProductsMapResourceLocation) {
        this.productIdToProductsMapResourceLocation = productIdToProductsMapResourceLocation;
    }

    public String getRoleToProductsMapResourceLocation() {
        return roleToProductsMapResourceLocation;
    }

    public void setRoleToProductsMapResourceLocation(String roleToProductsMapResourceLocation) {
        this.roleToProductsMapResourceLocation = roleToProductsMapResourceLocation;
    }

    public TallyRetentionPolicyProperties getTallyRetentionPolicy() {
        return tallyRetentionPolicy;
    }

    public String getAccountListResourceLocation() {
        return accountListResourceLocation;
    }

    public void setAccountListResourceLocation(String accountListResourceLocation) {
        this.accountListResourceLocation = accountListResourceLocation;
    }

    public int getHostLastSyncThresholdHours() {
        return hostLastSyncThresholdHours;
    }

    public void setHostLastSyncThresholdHours(int hostLastSyncThresholdHours) {
        this.hostLastSyncThresholdHours = hostLastSyncThresholdHours;
    }

    public int getAccountBatchSize() {
        return this.accountBatchSize;
    }

    public void setAccountBatchSize(int accountBatchSize) {
        this.accountBatchSize = accountBatchSize;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public String getProductWhitelistResourceLocation() {
        return productWhitelistResourceLocation;
    }

    public void setProductWhitelistResourceLocation(String productWhitelistResourceLocation) {
        this.productWhitelistResourceLocation = productWhitelistResourceLocation;
    }

    public String getReportingAccountWhitelistResourceLocation() {
        return reportingAccountWhitelistResourceLocation;
    }

    public void setReportingAccountWhitelistResourceLocation(String location) {
        this.reportingAccountWhitelistResourceLocation = location;
    }

    public Duration getAccountListCacheTtl() {
        return accountListCacheTtl;
    }

    public void setAccountListCacheTtl(Duration accountListCacheTtl) {
        this.accountListCacheTtl = accountListCacheTtl;
    }

    public Duration getProductIdToProductsMapCacheTtl() {
        return productIdToProductsMapCacheTtl;
    }

    public void setProductIdToProductsMapCacheTtl(Duration productIdToProductsMapCacheTtl) {
        this.productIdToProductsMapCacheTtl = productIdToProductsMapCacheTtl;
    }

    public Duration getProductWhiteListCacheTtl() {
        return productWhiteListCacheTtl;
    }

    public void setProductWhiteListCacheTtl(Duration productWhiteListCacheTtl) {
        this.productWhiteListCacheTtl = productWhiteListCacheTtl;
    }

    public Duration getRoleToProductsMapCacheTtl() {
        return roleToProductsMapCacheTtl;
    }

    public void setRoleToProductsMapCacheTtl(Duration roleToProductsMapCacheTtl) {
        this.roleToProductsMapCacheTtl = roleToProductsMapCacheTtl;
    }

    public Duration getReportingAccountWhitelistCacheTtl() {
        return reportingAccountWhitelistCacheTtl;
    }

    public void setReportingAccountWhitelistCacheTtl(Duration reportingAccountWhitelistCacheTtl) {
        this.reportingAccountWhitelistCacheTtl = reportingAccountWhitelistCacheTtl;
    }

    public int getCullingOffsetDays() {
        return cullingOffsetDays;
    }

    public void setCullingOffsetDays(int cullingOffsetDays) {
        this.cullingOffsetDays = cullingOffsetDays;
    }

    public String getAntiCsrfDomainSuffix() {
        return antiCsrfDomainSuffix;
    }

    public void setAntiCsrfDomainSuffix(String antiCsrfDomainSuffix) {
        this.antiCsrfDomainSuffix = antiCsrfDomainSuffix;
    }

    public int getAntiCsrfPort() {
        return antiCsrfPort;
    }

    public void setAntiCsrfPort(int antiCsrfPort) {
        this.antiCsrfPort = antiCsrfPort;
    }

    public void setOrgAdminOptional(boolean orgAdminOptional) {
        this.orgAdminOptional = orgAdminOptional;
    }

    public boolean isOrgAdminOptional() {
        return orgAdminOptional;
    }

    public String getRbacApplicationName() {
        return rbacApplicationName;
    }

    public void setRbacApplicationName(String rbacApplicationName) {
        this.rbacApplicationName = rbacApplicationName;
    }
}
