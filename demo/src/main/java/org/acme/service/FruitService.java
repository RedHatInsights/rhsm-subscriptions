package org.acme.service;

import java.util.List;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import org.acme.entity.Fruit;

@ApplicationScoped
public class FruitService {
  public String getFruit(String name) {

    Fruit.findByName(name);

    return "hello " + name;
  }

  public void saveExampleFruits() {

    for (String color : List.of("green", "red", "yellow")) {

      Fruit fruit = new Fruit();

      fruit.id = UUID.randomUUID().toString();
      fruit.name = "apple";
      fruit.color = color;

      Fruit.persist(fruit);

    }

  }
}
