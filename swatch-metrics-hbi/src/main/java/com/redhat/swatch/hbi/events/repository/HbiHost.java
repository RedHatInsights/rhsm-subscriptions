package com.redhat.swatch.hbi.events.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "hbi_host",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_subscription_manager_id",
          columnNames = {"subscription_manager_id"}),
      @UniqueConstraint(
          name = "uq_inventory_id",
          columnNames = {"inventory_id"})
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HbiHost extends PanacheEntityBase {

  @Id private UUID id;

  @Column(name = "org_id", nullable = false)
  private String orgId;

  @Column(name = "subscription_manager_id")
  private String subscriptionManagerId;

  @Column(name = "inventory_id")
  private String inventoryId;

  @Column(name = "display_name")
  private String displayName;

  @Lob
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "facts")
  private String facts;

  @Column(name = "creation_date", nullable = false)
  private OffsetDateTime creationDate;

  @Column(name = "last_updated", nullable = false)
  private OffsetDateTime lastUpdated;

  @PrePersist
  @PreUpdate
  public void updateTimestamps() {
    lastUpdated = OffsetDateTime.now();
    if (creationDate == null) {
      creationDate = lastUpdated;
    }
  }

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToMany(mappedBy = "hypervisor", fetch = FetchType.LAZY)
  private List<HypervisorGuestRelationship> guestLinks;

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToOne(mappedBy = "guest", fetch = FetchType.LAZY)
  private HypervisorGuestRelationship hypervisorLink;

  @Transient
  public boolean isUnmappedGuest() {
    return this.hypervisorLink == null;
  }

  public List<HbiHost> getGuests() {
    if (guestLinks == null) return List.of();
    return guestLinks.stream().map(HypervisorGuestRelationship::getGuest).toList();
  }

  public Optional<HbiHost> getHypervisor() {
    return Optional.ofNullable(hypervisorLink).map(HypervisorGuestRelationship::getHypervisor);
  }

  // TODO define a findNumOfGuests db call without having to load the whole guest list in memory to
  // do a .size() on

}
