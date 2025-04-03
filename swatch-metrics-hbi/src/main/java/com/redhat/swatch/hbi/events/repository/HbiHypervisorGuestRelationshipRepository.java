/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.hbi.events.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class HbiHypervisorGuestRelationshipRepository {

  @PersistenceContext EntityManager em;

  @Transactional
  public void persist(HbiHypervisorGuestRelationship relationship) {
    em.persist(relationship);
  }

  @Transactional
  public HbiHypervisorGuestRelationship merge(HbiHypervisorGuestRelationship relationship) {
    return em.merge(relationship);
  }

  @Transactional
  public void delete(HbiHypervisorGuestRelationship relationship) {
    if (em.contains(relationship)) {
      em.remove(relationship);
    } else {
      em.remove(em.merge(relationship));
    }
  }

  public Optional<HbiHypervisorGuestRelationship> findById(UUID id) {
    return Optional.ofNullable(em.find(HbiHypervisorGuestRelationship.class, id));
  }

  public List<HbiHypervisorGuestRelationship> findAll() {
    return em.createQuery(
            "FROM HbiHypervisorGuestRelationship", HbiHypervisorGuestRelationship.class)
        .getResultList();
  }

  public boolean existsByHypervisorAndGuest(HbiHost hypervisor, HbiHost guest) {
    Long count =
        em.createQuery(
                """
        SELECT COUNT(r)
        FROM HbiHypervisorGuestRelationship r
        WHERE r.hypervisor = :hypervisor AND r.guest = :guest
      """,
                Long.class)
            .setParameter("hypervisor", hypervisor)
            .setParameter("guest", guest)
            .getSingleResult();

    return count > 0;
  }

  public Optional<HbiHypervisorGuestRelationship> findByGuest(HbiHost guest) {
    return em.createQuery(
            """
        SELECT r FROM HbiHypervisorGuestRelationship r
        WHERE r.guest = :guest
      """,
            HbiHypervisorGuestRelationship.class)
        .setParameter("guest", guest)
        .getResultStream()
        .findFirst();
  }

  public List<HbiHypervisorGuestRelationship> findAllByHypervisor(HbiHost hypervisor) {
    return em.createQuery(
            """
        SELECT r FROM HbiHypervisorGuestRelationship r
        WHERE r.hypervisor = :hypervisor
      """,
            HbiHypervisorGuestRelationship.class)
        .setParameter("hypervisor", hypervisor)
        .getResultList();
  }

  @Transactional
  public void deleteAll() {
    em.createQuery("DELETE FROM HbiHypervisorGuestRelationship").executeUpdate();
  }
}
