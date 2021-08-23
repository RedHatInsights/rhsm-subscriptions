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

import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.product.api.model.AttributeValue;
import org.candlepin.subscriptions.product.api.model.EngineeringProduct;
import org.candlepin.subscriptions.product.api.model.OperationalProduct;
import org.candlepin.subscriptions.product.api.model.RESTProductTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides an easier way to translate an operational product, its children, and their derived
 * products into an Offering. The upstream product data are put into this intermediate structure
 * which can then be "merged down" into its ultimate Offering form.
 */
/* package-protected */ class UpstreamProductData {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamProductData.class);
  private static final String MSG_TEMPLATE =
      "sku=\"%s\" already has field=%s original=\"%s\" so will ignore value=\"%s\".";
  private static final int CONVERSION_RATIO_IFL_TO_CORES = 4;

  /** List of opProd attribute codes used in the making of an Offering. */
  // Non-standard attribute codes are prefixed by "X_". They are not actually attribute codes
  // from an opProd, but pretending as if they are will let us use the merge capabilities of Maps.
  private enum Attr {
    CORES,
    DERIVED_SKU,
    /** IBM-specific value to be used instead of CORES, if it is present. */
    IFL,
    SOCKET_LIMIT,
    SERVICE_TYPE,
    PRODUCT_FAMILY,
    USAGE,
    /** Name of Offering comes from opProd description field, not the PRODUCT_NAME attribute. */
    X_DESCRIPTION,
    /** Role originates from opProd roles field, not an attribute. */
    X_ROLE;
  }

  /** Maps opProd attribute codes to UpstreamProductData.Attrs of the same name. */
  private static final Map<String, Attr> CODE_TO_ENUM =
      Arrays.stream(Attr.values())
          .collect(Collectors.toUnmodifiableMap(Attr::name, attr -> attr, (a1, a2) -> a1));

  /** Ignore any product attribute whose value is in this set. */
  private static final Set<String> ATTRIBUTE_DISALLOWED_VALUES = Set.of("n/a", "0", "n", "none");

  private UpstreamProductData(String sku) {
    this.sku = sku;
  }

  private String sku;
  private SortedSet<String> children = new TreeSet<>();
  private SortedSet<Integer> engOids = new TreeSet<>();
  private EnumMap<Attr, String> attrs = new EnumMap<>(Attr.class);
  private List<String> conflicts = new ArrayList<>();

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
          .map(UpstreamProductData::createFromTree)
          .map(mid -> mid.fetchAndAddDerivedTreeIfExists(productService))
          .map(mid -> mid.fetchAndAddEngProdsIfExist(productService))
          .map(UpstreamProductData::toOffering);
    } catch (ApiException e) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          "Unable to retrieve upstream offering sku=\"" + sku + "\"",
          e);
    }
  }

  private static UpstreamProductData createFromTree(RESTProductTree skuTree) {
    var products = skuTree.getProducts();

    // Product trees returned by the product service always list the parent product first.
    var parent = products.get(0);
    var children = products.subList(1, products.size());

    var offer = new UpstreamProductData(parent.getSku());
    String name = parent.getDescription();
    offer.attrs.put(Attr.X_DESCRIPTION, name);
    // Though theoretically possible, none of the products in use by Candlepin (which includes the
    // allowlisted swatch products) ever has more than one role.
    String role = parent.getRoles().stream().findFirst().orElse(null);
    offer.attrs.put(Attr.X_ROLE, role);
    offer.mapAttributes(parent);

    // For each child, merge its unconflicting information into the parent.
    children.stream().map(UpstreamProductData::createFromProduct).forEach(offer::merge);

    return offer;
  }

  private Offering toOffering() {
    if (!conflicts.isEmpty()) {
      String conflictItems = String.join(System.lineSeparator(), conflicts);
      LOGGER.info(
          "Encountered conflicting attribute values when pulling offering sku={} from upstream:\n{}",
          sku,
          conflictItems);
    }

    Offering offering = new Offering();
    offering.setSku(sku);
    offering.setChildSkus(Set.copyOf(children));
    offering.setProductIds(Set.copyOf(engOids));
    offering.setRole(attrs.get(Attr.X_ROLE));
    offering.setProductFamily(attrs.get(Attr.PRODUCT_FAMILY));
    offering.setProductName(attrs.get(Attr.X_DESCRIPTION));

    calcCapacityForOffering(offering);

    /*
    Note 1: The DB model ServiceLevel currently maps to attr code "SERVICE_TYPE". This table and
    class may be renamed in the future since products can also have attr code "SERVICE_LEVEL" that
    might be used in the future.

    Note 2: ServiceLevel does not represent all values of SERVICE_TYPE seen in prod, which includes:
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

  /**
   * If the DERIVED_SKU attribute exists, will fetch the product tree of the derived SKU and merge
   * it with the offering. Merging means its attributes and its children's attributes will be used
   * in the offering if not yet yet, and the derived sku and its child skus will be added as child
   * skus of the offering.
   *
   * <p>It is unclear if derived SKUs <b>should</b> have the attributes merged and the derived SKUs
   * listed as children in the longer term. For now though, this simplification fits with how we
   * present offerings to the user. See:
   * https://docs.google.com/document/d/1t5OlyWanEpwXOA7ysPKuZW61cvYnIScwRMl--hmajXY/edit#heading=h.3aq1apsnbb0o
   *
   * @param productService the upstream service to fetch the derived product from
   * @return this UpstreamProductData (not the derived product), for chaining.
   */
  private UpstreamProductData fetchAndAddDerivedTreeIfExists(ProductService productService) {
    String derivedSku = attrs.get(Attr.DERIVED_SKU);
    if (derivedSku != null) {
      try {
        // derived SKUs are marketing SKUs, so need to get its service SKUs too and add
        // the SKUs to the list of children so the engOids are fetched.
        Optional<UpstreamProductData> derived =
            productService.getTree(derivedSku).map(UpstreamProductData::createFromTree);
        if (derived.isEmpty()) {
          LOGGER.warn(
              "No tree found for derivedSku=\"{}\" of offering sku=\"{}\"", derivedSku, sku);
        } else {
          merge(derived.get());
        }
      } catch (ApiException e) {
        throw new ExternalServiceException(
            ErrorCode.REQUEST_PROCESSING_ERROR,
            "Unable to retrieve derivedSku=\"" + derivedSku + "\" for offering sku=\"" + sku + "\"",
            e);
      }
    }
    return this;
  }

  private UpstreamProductData fetchAndAddEngProdsIfExist(ProductService productService) {
    /*
    Engineering Product OIDs need be fetched for all SKUs, including derived SKUs. For *most*
    VDC SKUs like RH00001 and its child SVCRH00001, neither of them will have associated engOIDs.
    but their derived SKU RH00049 has a child SVCRH00049 with associated engOIDs. So it is important
    to fetch engOIDs for derived SKUs.
    */
    Set<String> allSkus = allSkus();
    LOGGER.debug("Retrieving engOids for skus=\"{}\" of offering sku=\"{}\"", allSkus, sku);
    try {
      Map<String, List<EngineeringProduct>> engProds =
          productService.getEngineeringProductsForSkus(allSkus);
      addEngProds(engProds);
    } catch (ApiException e) {
      throw new ExternalServiceException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          "Unable to retrieve engOids of skus=\"" + allSkus + "\" for offering sku=" + sku,
          e);
    }

    return this;
  }

  private void addEngProds(Map<String, List<EngineeringProduct>> engProds) {
    engProds.values().stream()
        .flatMap(engProdList -> engProdList.stream().map(EngineeringProduct::getOid))
        .forEach(engOids::add);
  }

  /**
   * Get all SKUs involved in the offering, including the offering SKU, child SKUs, derived SKU, and
   * derived child SKUs.
   *
   * @return collection of all SKUs, or empty if none.
   */
  private Set<String> allSkus() {
    var skus = new HashSet<>(children);
    skus.add(sku);
    return skus;
  }

  private void calcCapacityForOffering(Offering offering) {
    // If IFL attr is defined, use it...
    Integer cores =
        Optional.ofNullable(attrs.get(Attr.IFL))
            .map(ifl -> Integer.parseInt(ifl) * CONVERSION_RATIO_IFL_TO_CORES)
            // ... but if IFL is not defined, then use the CORES attr.
            .orElseGet(() -> nullOrInteger(attrs.get(Attr.CORES)));

    Integer sockets = nullOrInteger(attrs.get(Attr.SOCKET_LIMIT));

    offering.setPhysicalCores(cores);
    offering.setPhysicalSockets(sockets);

    // If there is a derived SKU, then virtual cores and sockets match the physical values.
    if (attrs.get(Attr.DERIVED_SKU) != null) {
      offering.setVirtualCores(cores);
      offering.setVirtualSockets(sockets);
    }
  }

  private static Integer nullOrInteger(String v) {
    if (v == null) {
      return null;
    }
    return Integer.valueOf(v);
  }

  private static UpstreamProductData createFromProduct(OperationalProduct product) {
    var mid = new UpstreamProductData(product.getSku());
    mid.mapAttributes(product);

    return mid;
  }

  /**
   * Merges all unconflicting attributes from the child product into this product. In other words,
   * if this product has a non-null value for "CORES", and the child product also has a non-null
   * value for "CORES", then the value from the product is used.
   *
   * @param child the product to merge attributes from
   */
  private void merge(UpstreamProductData child) {
    children.add(child.sku);
    children.addAll(child.children);

    // Values already set by the parent (or by previous children) should not be overwritten.
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
      conflicts.add(msg);
    }

    return old;
  }

  private void mapAttributes(OperationalProduct opProd) {
    var prodAttrs = opProd.getAttributes();
    if (prodAttrs == null || prodAttrs.isEmpty()) {
      return;
    }
    for (AttributeValue sourceAttr : prodAttrs) {
      Attr destAttr = CODE_TO_ENUM.get(sourceAttr.getCode());
      String value = sourceAttr.getValue();
      if (destAttr != null && attributeIsAllowed(value)) {
        putIfNoConflict(destAttr, value);
      }
    }
  }

  /**
   * Checks if the value from the upstream product attribute is non-null and not an "empty" value.
   *
   * @param value The attribute value to check
   * @return true if the value can be used, false if not.
   */
  private static boolean attributeIsAllowed(String value) {
    return value != null && !ATTRIBUTE_DISALLOWED_VALUES.contains(value.toLowerCase(Locale.US));
  }
}
