package com.example.demo.controller;


import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.example.demo.dto.SwatchResponse;
import com.example.demo.model.Fruits;
import com.example.demo.service.FruitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rhsm-subscriptions/v1")
public class FruitController {

  private FruitService fruitService;

  @Autowired
  public FruitController(FruitService fruitService) {
    this.fruitService = fruitService;
  }

  @GetMapping("/fruits")
  public SwatchResponse getAllFruits() {
    return new SwatchResponse(fruitService.fetchAllFruits());

  }

  @PostMapping("/fruits")
  public SwatchResponse saveNewFruit() {

    var appleColors = Stream.of("red", "yellow", "green");

    var applesOfAllColors = appleColors.map(x -> {
      var newFruit = new Fruits();
      newFruit.setId(UUID.randomUUID().toString());
      newFruit.setName("apple");
      newFruit.setColor(x);
      return newFruit;
    }).collect(Collectors.toList());

    return new SwatchResponse(fruitService.saveNewFruit(applesOfAllColors));
  }

}
