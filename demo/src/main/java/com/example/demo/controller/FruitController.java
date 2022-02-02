package com.example.demo.controller;


import com.example.demo.dto.SwatchResponse;
import com.example.demo.service.FruitService;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
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

}
