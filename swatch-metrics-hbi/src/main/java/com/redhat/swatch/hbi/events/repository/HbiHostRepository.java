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
public class HbiHostRepository {

  @PersistenceContext EntityManager em;

  @Transactional
  public void persist(HbiHost host) {
    em.persist(host);
  }

  @Transactional
  public HbiHost merge(HbiHost host) {
    return em.merge(host);
  }

  @Transactional
  public void delete(HbiHost host) {
    if (em.contains(host)) {
      em.remove(host);
    } else {
      em.remove(em.merge(host));
    }
  }

  public Optional<HbiHost> findById(UUID id) {
    return Optional.ofNullable(em.find(HbiHost.class, id));
  }

  public List<HbiHost> findAll() {
    return em.createQuery("SELECT h FROM HbiHost h", HbiHost.class).getResultList();
  }

  public Optional<HbiHost> findBySubscriptionManagerId(String subMgrId) {
    return em.createQuery(
            """
        SELECT h FROM HbiHost h
        WHERE h.subscriptionManagerId = :subMgrId
      """,
            HbiHost.class)
        .setParameter("subMgrId", subMgrId)
        .getResultStream()
        .findFirst();
  }

  public Optional<HbiHost> findByInventoryId(String inventoryId) {
    return em.createQuery(
            """
        SELECT h FROM HbiHost h
        WHERE h.inventoryId = :inventoryId
      """,
            HbiHost.class)
        .setParameter("inventoryId", inventoryId)
        .getResultStream()
        .findFirst();
  }

  public List<HbiHost> findAllByOrgId(String orgId) {
    return em.createQuery(
            """
        SELECT h FROM HbiHost h
        WHERE h.orgId = :orgId
      """,
            HbiHost.class)
        .setParameter("orgId", orgId)
        .getResultList();
  }

  public long countGuestsForHypervisor(UUID hypervisorId) {
    return em.createQuery(
            """
        SELECT COUNT(r)
        FROM HbiHypervisorGuestRelationship r
        WHERE r.hypervisor.id = :hypervisorId
      """,
            Long.class)
        .setParameter("hypervisorId", hypervisorId)
        .getSingleResult();
  }

  public Optional<HbiHost> findByOrgIdAndSubscriptionManagerId(String orgId, String subMgrId) {
    return em.createQuery(
            """
      FROM HbiHost h
      WHERE h.orgId = :orgId AND h.subscriptionManagerId = :subMgrId
    """,
            HbiHost.class)
        .setParameter("orgId", orgId)
        .setParameter("subMgrId", subMgrId)
        .getResultStream()
        .findFirst();
  }

  @Transactional
  public void deleteAll() {
    em.createQuery("DELETE FROM HbiHost").executeUpdate();
  }

  public long countGuestsForHypervisor(HbiHost hypervisor) {
    return em.createQuery(
            """
      SELECT COUNT(r)
      FROM HbiHypervisorGuestRelationship r
      WHERE r.hypervisor = :hypervisor
    """,
            Long.class)
        .setParameter("hypervisor", hypervisor)
        .getSingleResult();
  }
}
