package com.redhat.swatch.resource;

import com.redhat.swatch.openapi.Fruit;
import com.redhat.swatch.openapi.FruitsApi;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.Path;
import com.redhat.swatch.service.FruitService;


@Path("/fruits")
public class FruitResource implements FruitsApi {

  @Inject
  FruitService fruitService;

  @Override
  public void createFruit(Fruit fruit) {

    fruitService.createFruitSaladRequest();

  }

  @Override
  public void deleteFruit(String fruitId) {

  }

  @Override
  public Fruit getFruit(String fruitId) {
    com.redhat.swatch.entity.Fruit fruit = fruitService.getFruit(fruitId);

    return new Fruit().name(fruit.name).color(fruit.color);
  }

  @Override
  public List<Fruit> getFruits() {
    return null;
  }

  @Override
  public void updateFruit(String fruitId, Fruit fruit) {

  }
}
