package org.candlepin.insights.inventory.client;

/**
 * Sub-class for inventory service properties
 */
public class InventoryServiceConfiguration {
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
}
