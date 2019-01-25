package org.candlepin.insights.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.insights.model.StatusMessage;

import org.junit.jupiter.api.Test;

public class StatusMessageControllerTest {
    @Test
    public void testStatusProvidesTimestampAndText() {
        StatusMessageController controller = new StatusMessageController();
        StatusMessage status = controller.createStatus();
        assertNotEquals(null, status.getStatusText(), "status should not be null");
        assertNotEquals(null, status.getTimestamp(), "timestamp should not be null");
    }
}
