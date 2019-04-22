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
package org.candlepin.insights.inventory;

import org.candlepin.insights.validator.ip.IpAddress;

import org.hibernate.validator.constraints.Length;

import java.util.List;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

/**
 * POJO that holds all facts scoped for collection by the conduit.
 */
public class ConduitFacts {

    private String subscriptionManagerId;
    private String biosUuid;
    private String orgId;

    private List<@IpAddress String> ipAddresses;

    @Length(min = 1, max = 255)

    private String fqdn;

    // See https://stackoverflow.com/a/4260512/6124862
    // Also a soft validation.  A mixed delimiter MAC like a1:b2-c3:d4-e5:f6 will still validate
    private List<@Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$") String> macAddresses;

    @Positive
    private Integer cpuSockets;

    @Positive
    private Integer cpuCores;

    @Positive
    private Integer memory;

    private String architecture;
    private Boolean isVirtual;
    private String vmHost;
    private List<String> rhProd;

    public String getSubscriptionManagerId() {
        return subscriptionManagerId;
    }

    public void setSubscriptionManagerId(String subscriptionManagerId) {
        this.subscriptionManagerId = subscriptionManagerId;
    }

    public String getBiosUuid() {
        return biosUuid;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public void setBiosUuid(String biosUuid) {
        this.biosUuid = biosUuid;
    }

    public List<String> getIpAddresses() {
        return ipAddresses;
    }

    public void setIpAddresses(List<String> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    public String getFqdn() {
        return fqdn;
    }

    public void setFqdn(String fqdn) {
        this.fqdn = fqdn;
    }

    public List<String> getMacAddresses() {
        return macAddresses;
    }

    public void setMacAddresses(List<String> macAddresses) {
        this.macAddresses = macAddresses;
    }

    public Integer getCpuSockets() {
        return cpuSockets;
    }

    public void setCpuSockets(Integer cpuSockets) {
        this.cpuSockets = cpuSockets;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Integer getMemory() {
        return memory;
    }

    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getVmHost() {
        return vmHost;
    }

    public Boolean getVirtual() {
        return isVirtual;
    }

    public void setVirtual(Boolean virtual) {
        isVirtual = virtual;
    }

    public void setVmHost(String vmHost) {
        this.vmHost = vmHost;
    }

    public List<String> getRhProd() {
        return rhProd;
    }

    public void setRhProd(List<String> rhProd) {
        this.rhProd = rhProd;
    }
}
