package com.example.demo.service;

import java.util.Collection;
import java.util.List;
import com.example.demo.model.Fruits;
import com.example.demo.repository.FruitRepository;
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

  public List<Fruits> saveNewFruit(Collection<Fruits> fruitsToSave) {
    return fruitRepository.saveAll(fruitsToSave);
  }
}
