package org.acme.resource;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.acme.service.FruitService;

@Path("/fruits")
public class FruitResource {

  @Inject
  FruitService fruitService;

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/{fruitName}")
  public String getAllFruits(@PathParam("fruitName") String fruitName) {
    return fruitService.getFruit(fruitName);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional
  public String insertFruits() {
    fruitService.createFruitSaladRequest();
    return "done";
  }
}
