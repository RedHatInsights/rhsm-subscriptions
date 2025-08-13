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
package com.redhat.swatch.hbi.events.services;

import com.redhat.swatch.hbi.events.model.OutboxRecordMapper;
import com.redhat.swatch.hbi.events.repository.HbiEventOutbox;
import com.redhat.swatch.hbi.events.repository.HbiEventOutboxRepository;
import com.redhat.swatch.hbi.model.OutboxRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.candlepin.subscriptions.json.Event;

@AllArgsConstructor
@ApplicationScoped
public class HbiEventOutboxService {

  private final HbiEventOutboxRepository repository;
  private final OutboxRecordMapper mapper;

  @Transactional
  public OutboxRecord createOutboxRecord(Event event) {
    HbiEventOutbox entity = new HbiEventOutbox();
    entity.setOrgId(event.getOrgId());
    entity.setSwatchEventJson(event);
    repository.persistAndFlush(entity);
    return mapper.entityToDto(entity);
  }
}
