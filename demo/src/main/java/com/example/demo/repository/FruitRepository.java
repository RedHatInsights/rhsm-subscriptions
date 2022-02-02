package com.example.demo.repository;

import com.example.demo.model.Fruits;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FruitRepository extends JpaRepository<Fruits, String> {

}
