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
package org.candlepin.subscriptions.product;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.product.api.model.AttributeValue;
import org.candlepin.subscriptions.product.api.model.EngineeringProduct;
import org.candlepin.subscriptions.product.api.model.OperationalProduct;
import org.candlepin.subscriptions.product.api.model.RESTProductTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides an easier way to translate an operational product, its children, and their derived
 * products into an Offering. The upstream product data are put into this intermediate structure
 * which can then be "merged down" into its ultimate Offering form.
 */
public class MidProduct {

  private static final Logger LOGGER = LoggerFactory.getLogger(MidProduct.class);
  private static String MSG_TEMPLATE =
      "sku=\"%s\" already has field=%s original=\"%s\" so will ignore value=\"%s\".";
  private static final int CONVERSION_RATIO_IFL_TO_CORES = 4;

  /** List of opProd attribute codes used in the making of an Offering. */
  // Non-standard attribute codes are prefixed by "__X_". They are not actually attribute codes
  // from an opProd, but pretending as if they are will let us use the merge capabilities of Maps.
  private enum Attr {
    CORES,
    DERIVED_SKU,
    /** IBM-specific value to be used instead of CORES, if it is present. */
    IFL,
    SOCKET_LIMIT,
    SERVICE_TYPE,
    PRODUCT_FAMILY,
    PRODUCT_NAME,
    USAGE,
    /** Originates from opProd attr code "CORES" when coming from a derived product. */
    __X_VIRTUAL_CORES,
    /** Originates from opProd attr code "IFL" when coming from a derived product. */
    __X_VIRTUAL_IFL,
    /** Originates from opProd attr code "SOCKET_LIMIT" when coming from a derived product. */
    __X_VIRTUAL_SOCKETS,
    /** Role originates from opProd field role, not an attribute. */
    __X_ROLE;
  }

  /** Maps opProd attribute codes to MidProduct.Attrs of the same name. */
  private static final Map<String, Attr> CODE_TO_ENUM =
      Arrays.stream(Attr.values())
          .collect(Collectors.toMap(Attr::name, attr -> attr, (a1, a2) -> a1));

  private MidProduct(String sku) {
    this.sku = sku;
  }

  private String sku;
  private SortedSet<String> children = new TreeSet<>();
  private SortedSet<Integer> engOids = new TreeSet<>();
  private EnumMap<Attr, String> attrs = new EnumMap<>(Attr.class);
  private StringBuilder conflicts = new StringBuilder(0);

  /**
   * Create an {@link Offering} based on product service data from upstream.
   *
   * @param sku the identifier of the marketing operational product
   * @param productService the upstream product service
   * @return An Offering with information filled by an upstream service, or empty if the product was
   *     not found.
   */
  public static Optional<Offering> offeringFromUpstream(String sku, ProductService productService) {
    LOGGER.debug("Retrieving product tree for offering sku=\"{}\"", sku);

    try {
      return productService
          .getTree(sku)
          .map(MidProduct::createFromTree)
          .map(mid -> mid.fetchAndAddDerivedTreeIfExist(productService))
          .map(mid -> mid.fetchAndAddEngProdsIfExist(productService))
          .map(MidProduct::toOffering);
    } catch (ApiException e) {
      throw new RuntimeException("Unable to retrieve upstream offering sku=\"" + sku + "\"", e);
    }
  }

  private static MidProduct createFromTree(RESTProductTree skuTree) {
    var products = skuTree.getProducts();

    // Product trees returned by the product service always list the parent product first.
    var parent = products.get(0);
    var children = products.subList(1, products.size());

    var offer = new MidProduct(parent.getSku());
    // Though theoretically possible, none of the products in use by Candlepin (which includes the
    // allowlisted swatch products) ever has more than one role.
    String role = parent.getRoles().stream().findFirst().orElse(null);
    offer.attrs.put(Attr.__X_ROLE, role);
    offer.mapAttributes(parent);

    // For each child, merge its unconflcting information into the parent.
    children.stream().map(MidProduct::createFromProduct).forEach(offer::merge);

    return offer;
  }

  private Offering toOffering() {
    if (conflicts.length() != 0) {
      LOGGER.info(
          "Encountered conflicting attribute values when pulling offering sku={} from upstream:\n{}",
          sku,
          conflicts);
    }

    Offering offering = new Offering();
    offering.setSku(sku);
    offering.setChildSkus(List.copyOf(children));
    offering.setProductIds(List.copyOf(engOids));
    offering.setRole(attrs.get(Attr.__X_ROLE));
    offering.setProductFamily(attrs.get(Attr.PRODUCT_FAMILY));
    offering.setProductName(attrs.get(Attr.PRODUCT_NAME));

    calcCoresAndSocketsForOffering(offering);

    /*
    The DB model ServiceLevel currently maps to attr code "SERVICE_TYPE". This table and class
    may be renamed in the future since products can also have attr code "SERVICE_LEVEL" that
    might be used in the future.
    */

    /*
    ServiceLevel does not represent all values of SERVICE_TYPE seen in prod, which includes:
    Basic, Dev-Professional, Layered, Premium, RHX Basic, Self-Support, Standard.

    Any unmapped SERVICE_TYPEs default to ServiceLevel.EMPTY, as done in
    org.candlepin.subscriptions.capacity.CandlepinPoolCapacityMapper.

    For discussions on this topic, see:
    https://docs.google.com/document/d/1t5OlyWanEpwXOA7ysPKuZW61cvYnIScwRMl--hmajXY/edit#heading=h.8dadhnye8ysf
    */
    var serviceType = attrs.get(Attr.SERVICE_TYPE);
    if (serviceType != null) {
      var level = ServiceLevel.fromString(serviceType);
      if (level == ServiceLevel.EMPTY) {
        LOGGER.warn("Offering sku=\"{}\" has unsupported SERVICE_TYPE=\"{}\"", sku, serviceType);
      }
      offering.setServiceLevel(level);
    }
    var usageVal = attrs.get(Attr.USAGE);
    if (usageVal != null) {
      var usage = Usage.fromString(usageVal);
      if (usage == Usage.EMPTY) {
        LOGGER.warn("Offering sku=\"{}\" has unsupported USAGE=\"{}\"", sku, usageVal);
      }
      offering.setUsage(usage);
    }

    return offering;
  }

  /*
   * If there is a derived SKU, its tree and engOids need fetched from upstream too. The derived
   * SKU and child will be added as children to the offering.
   *
   * <p>Note: it is unclear if derived SKUs *should* be listed as children in the longer term.
   * For now though, this simplification fits with how we present offerings to the user.
   * See:
   * https://docs.google.com/document/d/1t5OlyWanEpwXOA7ysPKuZW61cvYnIScwRMl--hmajXY/edit#heading=h.3aq1apsnbb0o
   *
   * <p>Example: SKU RH00001, child SKU SVCRH00001, have attr DERIVED_SKU = "RH00049".
   * Neither RH00001 or SVCRH00001 have engOids, but RH00049's child SKU SVCRH00049 does.
   */
  private MidProduct fetchAndAddDerivedTreeIfExist(ProductService productService) {
    String derivedSku = attrs.get(Attr.DERIVED_SKU);
    if (derivedSku != null) {
      try {
        // derived SKUs are marketing SKUs, so need to get its service SKUs too and add
        // the SKUs to the list of children so the engOids are fetched.
        Optional<MidProduct> mid = productService.getTree(derivedSku).map(this::addDerivedTree);
        if (mid.isEmpty()) {
          LOGGER.warn(
              "No tree found for derivedSku=\"{}\" of offering sku=\"{}\"", derivedSku, sku);
        }
      } catch (ApiException e) {
        throw new RuntimeException(
            "Unable to retrieve derivedSku=\"" + derivedSku + "\" for offering sku=\"" + sku + "\"",
            e);
      }
    }
    return this;
  }

  private MidProduct fetchAndAddEngProdsIfExist(ProductService productService) {
    Set<String> allSkus = allSkus();
    LOGGER.debug("Retrieving engOids for skus=\"{}\" of offering sku=\"{}\"", allSkus, sku);
    try {
      Map<String, List<EngineeringProduct>> engProds =
          productService.getEngineeringProductsForSkus(allSkus);
      addEngProds(engProds);
    } catch (ApiException e) {
      throw new RuntimeException(
          "Unable to retrieve engOids of skus=\"" + allSkus + "\" for offering sku=" + sku, e);
    }

    return this;
  }

  private MidProduct addDerivedTree(RESTProductTree derivedTree) {
    derivedTree.getProducts().stream()
        .map(MidProduct::createFromDerivedProduct)
        .forEach(this::merge);
    return this;
  }

  private void addEngProds(Map<String, List<EngineeringProduct>> engProds) {
    engProds.values().stream()
        .flatMap(engProdList -> engProdList.stream().map(engProd -> engProd.getOid()))
        .forEach(engOids::add);
  }

  /**
   * Get all SKUs involved in the offering, including the offering SKU, child SKUs, and derived SKU.
   *
   * @return collection of all SKUs, or empty if none.
   */
  private Set<String> allSkus() {
    var skus = new HashSet<>(children);
    skus.add(sku);
    return skus;
  }

  private void calcCoresAndSocketsForOffering(Offering offering) {
    String ifl = attrs.get(Attr.IFL);
    // ifl takes precedence over cores, so don't set cores if ifl exists.
    String cores = (ifl == null) ? attrs.get(Attr.CORES) : null;
    String sockets = attrs.get(Attr.SOCKET_LIMIT);

    String virtIfl = attrs.get(Attr.__X_VIRTUAL_IFL);
    // likewise for virtual cores, virtual ifl takes precedence if it exists.
    String virtCores = attrs.get(Attr.__X_VIRTUAL_CORES);
    String virtSockets = attrs.get(Attr.__X_VIRTUAL_SOCKETS);

    if (ifl == null) {
      offering.setPhysicalCores(nullOrInteger(cores));
    } else {
      int numCores = Integer.parseInt(ifl) * CONVERSION_RATIO_IFL_TO_CORES;
      offering.setPhysicalCores(numCores);
    }
    offering.setPhysicalSockets(nullOrInteger(sockets));

    if (virtIfl == null) {
      offering.setVirtualCores(nullOrInteger(virtCores));
    } else {
      int numVirtCores = Integer.parseInt(virtIfl) * CONVERSION_RATIO_IFL_TO_CORES;
      offering.setVirtualCores(numVirtCores);
    }
    offering.setVirtualSockets(nullOrInteger(virtSockets));
  }

  private static Integer nullOrInteger(String v) {
    if (v == null) {
      return null;
    }
    return Integer.valueOf(v);
  }

  private static MidProduct createFromProduct(OperationalProduct product) {
    var mid = new MidProduct(product.getSku());
    mid.mapAttributes(product);

    return mid;
  }

  private static MidProduct createFromDerivedProduct(OperationalProduct product) {
    var derived = new MidProduct(product.getSku());
    derived.mapAttributes(product);

    // Derived products actually define the number of virtual cores and virtual sockets, NOT the
    // physical cores and sockets, those values need moved to the proper Attr if set.
    String virtualCores = derived.attrs.get(Attr.CORES);
    if (virtualCores != null) {
      derived.putIfNoConflict(Attr.__X_VIRTUAL_CORES, virtualCores);
      derived.attrs.remove(Attr.CORES);
    }

    String virtualSockets = derived.attrs.get(Attr.SOCKET_LIMIT);
    if (virtualSockets != null) {
      derived.putIfNoConflict(Attr.__X_VIRTUAL_SOCKETS, virtualSockets);
      derived.attrs.remove(Attr.SOCKET_LIMIT);
    }

    return derived;
  }

  /**
   * Merges all unconflicting attributes from the child product into this product. In other words,
   * if this product has a non-null value for "CORES", and the child product also has a non-null
   * value for "CORES", then the value from the product is used.
   *
   * @param child the product to merge attributes from
   */
  private void merge(MidProduct child) {
    children.add(child.sku);

    // Values already set by the parent (or by previous children) should not be overwritten.
    //    child.attrs.forEach(attrs::putIfAbsent);
    child.attrs.forEach(this::putIfNoConflict);

    // Eng OIDs from the child are not added since they haven't been collected yet. Instead all of
    // the SKUs are collected (parent, children, derived) first and then all Eng OIDs are fetched.
  }

  /**
   * If the specified key is already associated with a different value, then a conflict entry is
   * logged and the original value is returned, otherwise if there was no associated value (or is
   * mapped to null) associates it with the given value and returns null, else returns the current
   * valuekey – key with which the specified value is to be associated value – value to be
   * associated with the specified key
   *
   * @param key key with which the specified value is to be associated value
   * @param val value to be associated with the specified key
   * @return previous value if already associated with a value, or null if value was set.
   */
  private String putIfNoConflict(Attr key, String val) {
    String old = attrs.putIfAbsent(key, val);

    if (old != null && !val.equals(old)) {
      var msg = String.format(MSG_TEMPLATE, sku, key, old, val);
      conflicts.append(msg).append('\n');
    }

    return old;
  }

  private void mapAttributes(OperationalProduct opProd) {
    var attrs = opProd.getAttributes();
    if (attrs == null || attrs.isEmpty()) {
      return;
    }
    for (AttributeValue sourceAttr : attrs) {
      Attr destAttr = CODE_TO_ENUM.get(sourceAttr.getCode());
      if (destAttr != null) {
        putIfNoConflict(destAttr, sourceAttr.getValue());
      }
    }
  }
}
