/*
 * Copyright Red Hat, Inc.
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
package org.candlepin.subscriptions.tally.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilterModifyingConfigurer;
import org.candlepin.subscriptions.security.WithMockPskPrincipal;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@WebAppConfiguration
@ActiveProfiles({"worker", "test"})
class InternalTallyResourceTest {

  public static final String PAYG_ROLLUPS = "/internal/tally/emit-payg-rollups";
  @Autowired WebApplicationContext context;

  private MockMvc mvc;

  @BeforeEach
  public void setup() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(new IdentityHeaderAuthenticationFilterModifyingConfigurer())
            .build();
  }

  @Test
  @WithMockPskPrincipal
  void testEmitPaygRollupsWithPsk() throws Exception {
    /* Why does this test expect isNotFound()?  Because we are using JAX-RS for our request
     * mapping. MockMvc only works with Spring's custom RestController standard, but it's really
     * handy to use for setting up the Spring Security filter chain.  It's a dirty hack, but we
     * can use MockMvc to test authentication and authorization by looking for a 403 response and
     * if we get a 404 response, it means everything passed security-wise and we just couldn't
     * find the matching resource (because there are no matching RestControllers!).
     */
    mvc.perform(
            post(PAYG_ROLLUPS)
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("date", "2022-05-01"))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockRedHatPrincipal("123")
  void testEmitPaygRollupsWithRedhatPrincipal() throws Exception {
    mvc.perform(
            post("/internal/tally/emit-payg-rollups")
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("date", "2022-05-01"))
        .andExpect(status().isForbidden());
  }

  @Test
  void testEmitPaygRollupsNoAuth() throws Exception {
    mvc.perform(
            post("/internal/tally/emit-payg-rollups")
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("date", "2022-05-01"))
        .andExpect(status().isUnauthorized());
  }
}
