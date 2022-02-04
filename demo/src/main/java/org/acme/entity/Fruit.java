package org.acme.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.ToString;


@ToString
@Entity
public class Fruit extends PanacheEntityBase {

  @Id
  @Column(length = 36)
  public String id;

  @Column
  public String name;

  @Column
  public String color;

  public static Fruit findByName(String name) {
    return find("name", name).firstResult();
  }

}
