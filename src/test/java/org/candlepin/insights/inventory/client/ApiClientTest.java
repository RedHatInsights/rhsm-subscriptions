package org.candlepin.insights.inventory.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class ApiClientTest {
    @Autowired
    private ApiClient client;

    @Test
    public void testServiceUrlConfigurableViaProperties() {
        assertEquals("https://localhost/api/hostinventory", client.getBasePath());
    }
}
