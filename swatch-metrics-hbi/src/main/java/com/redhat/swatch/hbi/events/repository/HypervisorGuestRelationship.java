package com.redhat.swatch.hbi.events.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "hypervisor_guest_relationship")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HypervisorGuestRelationship extends PanacheEntityBase {

  @Id private UUID id;

  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "hypervisor_host_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_hypervisor_host_id"))
  private HbiHost hypervisor;

  @ToString.Exclude
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "guest_host_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_guest_host_id"),
      unique = true) // Ensure 1-to-1 mapping from guest to hypervisor
  private HbiHost guest;
}
