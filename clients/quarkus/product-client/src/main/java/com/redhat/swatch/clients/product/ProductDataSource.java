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
package com.redhat.swatch.clients.product;

import com.redhat.swatch.clients.product.api.model.EngineeringProduct;
import com.redhat.swatch.clients.product.api.model.RESTProductTree;
import com.redhat.swatch.clients.product.api.resources.ApiException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Abstraction to allow product data to be pulled from API or provided directly. */
public interface ProductDataSource {

  Optional<RESTProductTree> getTree(String sku) throws ApiException;

  Map<String, List<EngineeringProduct>> getEngineeringProductsForSkus(Collection<String> skus)
      throws ApiException;
}
