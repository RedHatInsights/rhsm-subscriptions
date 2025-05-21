package com.swatch.product.config;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

  ProductService productService;

  @Inject
  public ProductResource(ProductService productService) {
    this.productService = productService;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/query")
  public Response queryProducts(ProductTagLookupParams params) {
    List<String> productIds = productService.findMatchingProductIds(params);
    return Response.ok(productIds).build();
  }
}
