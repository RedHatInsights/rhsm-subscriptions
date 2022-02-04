package com.redhat.swatch.service;

import com.redhat.swatch.entity.Fruit;
import java.util.List;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class FruitService {

  private static final String EGRESS_CHANNEL = "egress";

  public String getFruit(String name) {

    Fruit.findByName(name);

    return "hello " + name;
  }

  public void saveFruitToDatabase() {

    for (String color : List.of("green", "red", "yellow")) {

      Fruit fruit = createFruit(color);

      Fruit.persist(fruit);
    }
  }

  private Fruit createFruit(String color) {
    Fruit fruit = new Fruit();

    fruit.id = UUID.randomUUID().toString();
    fruit.name = "apple";
    fruit.color = color;
    return fruit;
  }

  @Channel(EGRESS_CHANNEL)
  Emitter<String> fruitSaladRequestEmitter;

  public String createFruitSaladRequest() {
    var fruit = createFruit("purple");
    fruitSaladRequestEmitter.send(fruit.toString());
    return fruit.toString();
  }
}
