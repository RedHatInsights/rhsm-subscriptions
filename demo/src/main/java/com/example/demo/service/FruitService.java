package com.example.demo.service;

import com.example.demo.model.Fruits;
import com.example.demo.repository.FruitRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FruitService {

  private final FruitRepository fruitRepository;

  public FruitService(FruitRepository fruitRepository) {
    this.fruitRepository = fruitRepository;
  }

  public List<Fruits> fetchAllFruits() {
    return fruitRepository.findAll();
  }
}
