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
package org.candlepin.subscriptions.conduit;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.candlepin.subscriptions.conduit.inventory.ConduitFacts;
import org.candlepin.subscriptions.conduit.inventory.InventoryService;
import org.candlepin.subscriptions.conduit.job.OrgSyncTaskManager;
import org.candlepin.subscriptions.conduit.rhsm.RhsmService;
import org.candlepin.subscriptions.conduit.rhsm.client.ApiException;
import org.candlepin.subscriptions.conduit.rhsm.client.model.Consumer;
import org.candlepin.subscriptions.conduit.rhsm.client.model.InstalledProducts;
import org.candlepin.subscriptions.conduit.rhsm.client.model.Pagination;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.inventory.client.InventoryServiceProperties;
import org.candlepin.subscriptions.inventory.client.model.NetworkInterface;
import org.candlepin.subscriptions.utilization.api.model.OrgInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Controller used to interact with the Inventory service. */
@Component
public class InventoryController {
  private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

  private static final BigDecimal KIBIBYTES_PER_GIBIBYTE = BigDecimal.valueOf(1048576);
  private static final BigDecimal BYTES_PER_KIBIBYTE = BigDecimal.valueOf(1024);
  private static final String COMMA_REGEX = ",\\s*";
  private static final String PERIOD_REGEX = "\\.";
  private static final String NON_HYPHEN_REGEX = "[0-9a-fA-F]{8}([0-9a-fA-F]{4}){3}[0-9a-fA-F]{12}";
  private static final String UUID_REGEX = "[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}";

  public static final String OS_DISTRIBUTION_NAME = "distribution.name";
  public static final String OS_DISTRIBUTION_VERSION = "distribution.version";
  public static final String DMI_SYSTEM_UUID = "dmi.system.uuid";
  public static final String DMI_BIOS_VERSION = "dmi.bios.version";
  public static final String DMI_BIOS_VENDOR = "dmi.bios.vendor";
  public static final String MAC_PREFIX = "net.interface.";
  public static final String MAC_SUFFIX = ".mac_address";

  // We should instead pull ip addresses from the following facts:
  //
  // net.interface.%.ipv4_address_list
  //      (or net.interface.%.ipv4_address if the list isn't present)
  // net.interface.%.ipv6_address.global_list
  //      (or net.interface.%.ipv6_address.global if the list isn't present)
  // net.interface.%.ipv6_address.link_list
  //      (or net.interface.%.ipv6_address.link if the list isn't present)
  public static final String IP_ADDRESS_FACT_REGEX =
      "^net\\.interface\\.[^.]*\\.ipv[46]_address(\\.global|\\.link)?(_list)?$";
  public static final String NETWORK_FQDN = "network.fqdn";
  public static final String CPU_SOCKETS = "cpu.cpu_socket(s)";
  public static final String CPU_CORES_PER_SOCKET = "cpu.core(s)_per_socket";
  public static final String MEMORY_MEMTOTAL = "memory.memtotal";
  public static final String UNAME_MACHINE = "uname.machine";
  public static final String VIRT_IS_GUEST = "virt.is_guest";
  public static final String INSIGHTS_ID = "insights_id";
  public static final String OPENSHIFT_CLUSTER_UUID = "openshift.cluster_uuid";
  public static final String OCM_UNITS = "ocm.units";
  public static final String OCM_BILLING_MODEL = "ocm.billing_model";
  public static final String UNKNOWN = "unknown";
  public static final String TRUE = "True";
  public static final String NONE = "none";
  public static final Set<String> IGNORED_CONSUMER_TYPES = Set.of("candlepin", "satellite", "sam");

  private InventoryService inventoryService;
  private RhsmService rhsmService;
  private Validator validator;
  private OrgSyncTaskManager taskManager;
  private Counter queueNextPageCounter;
  private Counter finalizeOrgCounter;
  private Timer transformHostTimer;
  private Timer validateHostTimer;

  @Autowired
  public InventoryController(
      InventoryService inventoryService,
      RhsmService rhsmService,
      Validator validator,
      InventoryServiceProperties inventoryServiceProperties,
      OrgSyncTaskManager taskManager,
      MeterRegistry meterRegistry) {

    this.inventoryService = inventoryService;
    this.rhsmService = rhsmService;
    this.validator = validator;
    this.taskManager = taskManager;
    this.queueNextPageCounter = meterRegistry.counter("rhsm-conduit.queue.next-page");
    this.finalizeOrgCounter = meterRegistry.counter("rhsm-conduit.finalize.org");
    this.transformHostTimer = meterRegistry.timer("rhsm-conduit.transform.host");
    this.validateHostTimer = meterRegistry.timer("rhsm-conduit.validate.host");
  }

  public ConduitFacts getFactsFromConsumer(Consumer consumer) {
    final Map<String, String> rhsmFacts = consumer.getFacts();
    ConduitFacts facts = new ConduitFacts();
    facts.setOrgId(consumer.getOrgId());
    String clusterUuid = rhsmFacts.get(OPENSHIFT_CLUSTER_UUID);
    // NOTE future displayName logic could consider more facts here
    if (clusterUuid != null) {
      facts.setDisplayName(clusterUuid);
    }
    facts.setSubscriptionManagerId(normalizeUuid(consumer.getUuid()));
    facts.setInsightsId(normalizeUuid(rhsmFacts.get(INSIGHTS_ID)));

    if (consumer.getLastCheckin() != null) {
      facts.setLastCheckin(consumer.getLastCheckin());
    }

    facts.setSysPurposeRole(consumer.getSysPurposeRole());
    facts.setSysPurposeSla(consumer.getServiceLevel());
    facts.setSysPurposeUsage(consumer.getSysPurposeUsage());
    facts.setSysPurposeAddons(consumer.getSysPurposeAddons());
    facts.setSysPurposeUnits(rhsmFacts.get(OCM_UNITS));
    facts.setBillingModel(rhsmFacts.get(OCM_BILLING_MODEL));

    extractNetworkFacts(rhsmFacts, facts);
    extractHardwareFacts(rhsmFacts, facts);
    extractVirtualizationFacts(consumer, rhsmFacts, facts);
    facts.setCloudProvider(extractCloudProvider(rhsmFacts));

    List<String> productIds =
        consumer.getInstalledProducts().stream()
            .map(InstalledProducts::getProductId)
            .collect(Collectors.toList());
    facts.setRhProd(productIds);

    return facts;
  }

  private String extractCloudProvider(Map<String, String> rhsmFacts) {
    String assetTag = rhsmFacts.getOrDefault("dmi.chassis.asset_tag", "");
    String biosVendor = rhsmFacts.getOrDefault(DMI_BIOS_VENDOR, "");
    String biosVersion = rhsmFacts.getOrDefault(DMI_BIOS_VERSION, "");
    String systemManufacturer = rhsmFacts.getOrDefault("dmi.system.manufacturer", "");
    if (assetTag.equals("7783-7084-3265-9085-8269-3286-77")) {
      return "azure";
    } else if (biosVendor.toLowerCase().contains("google")) {
      return "google";
    } else if (biosVersion.toLowerCase().contains("amazon")) {
      return "aws";
    } else if (systemManufacturer.toLowerCase().contains("alibaba")) {
      return "alibaba";
    }
    return null;
  }

  private String normalizeUuid(String uuid) {
    if (!StringUtils.hasText(uuid)) {
      return null;
    }
    String trimmed = uuid.trim();
    if (trimmed.contains("-")) {
      return trimmed;
    } else {
      return String.join(
          "-",
          trimmed.substring(0, 8),
          trimmed.substring(8, 12),
          trimmed.substring(12, 16),
          trimmed.substring(16, 20),
          trimmed.substring(20));
    }
  }

  private void extractHardwareFacts(Map<String, String> rhsmFacts, ConduitFacts facts) {
    String systemUuid = rhsmFacts.get(DMI_SYSTEM_UUID);
    if (StringUtils.hasLength(systemUuid)) {
      if (systemUuid.matches(UUID_REGEX)) {
        facts.setBiosUuid(systemUuid);
      } else if (systemUuid.matches(NON_HYPHEN_REGEX)) {
        facts.setBiosUuid(normalizeUuid(systemUuid));
      } else {
        log.info(
            "Consumer {} in org {} has unparseable BIOS uuid: {}",
            facts.getSubscriptionManagerId(),
            facts.getOrgId(),
            systemUuid);
      }
    }

    facts.setOsName(rhsmFacts.get(OS_DISTRIBUTION_NAME));
    facts.setOsVersion(rhsmFacts.get(OS_DISTRIBUTION_VERSION));

    facts.setBiosVersion(rhsmFacts.get(DMI_BIOS_VERSION));
    facts.setBiosVendor(rhsmFacts.get(DMI_BIOS_VENDOR));

    String cpuSockets = rhsmFacts.get(CPU_SOCKETS);
    String coresPerSocket = rhsmFacts.get(CPU_CORES_PER_SOCKET);
    if (StringUtils.hasLength(cpuSockets)) {
      Integer numCpuSockets = Integer.parseInt(cpuSockets);
      facts.setCpuSockets(numCpuSockets);
      if (StringUtils.hasLength(coresPerSocket)) {
        Integer numCoresPerSocket = Integer.parseInt(coresPerSocket);
        facts.setCpuCores(numCoresPerSocket * numCpuSockets);
      }
    }
    if (StringUtils.hasLength(coresPerSocket)) {
      facts.setCoresPerSocket(Integer.parseInt(coresPerSocket));
    }

    String memoryTotal = rhsmFacts.get(MEMORY_MEMTOTAL);
    if (StringUtils.hasLength(memoryTotal)) {
      try {
        BigDecimal memoryBytes = memtotalFromString(memoryTotal);
        // memtotal is a little less than accessible memory, round up to next GB
        long memoryGigabytes =
            memoryBytes.divide(KIBIBYTES_PER_GIBIBYTE, RoundingMode.CEILING).longValue();
        facts.setMemory(memoryGigabytes);
        facts.setSystemMemoryBytes(memoryBytes.multiply(BYTES_PER_KIBIBYTE).longValue());
      } catch (NumberFormatException e) {
        log.info("Bad memory.memtotal value: {}", memoryTotal);
      }
    }

    String architecture = rhsmFacts.get(UNAME_MACHINE);
    if (StringUtils.hasLength(architecture)) {
      facts.setArchitecture(architecture);
    }
  }

  /**
   * Return memorytotal in kibibytes, as seen in /proc/meminfo
   *
   * @param memoryTotal memory fact as a string
   * @return memory total in kibibytes
   */
  protected BigDecimal memtotalFromString(String memoryTotal) {
    // Check for match of openshift
    String patternString = "^\\d+\\.\\d+[Bb]$";
    Matcher matcher = Pattern.compile(patternString).matcher(memoryTotal);

    String memStr = memoryTotal;
    // Any other format will throw a NumberFormatException if not a double.
    if (matcher.matches()) {
      memStr = memStr.replaceAll("[Bb]", "");
      return new BigDecimal(memStr).divide(BYTES_PER_KIBIBYTE, RoundingMode.CEILING);
    } else {
      return new BigDecimal(memStr);
    }
  }

  @SuppressWarnings("indentation")
  private void extractNetworkFacts(Map<String, String> rhsmFacts, ConduitFacts facts) {
    String fqdn = rhsmFacts.get(NETWORK_FQDN);
    if (StringUtils.hasLength(fqdn)) {
      facts.setFqdn(fqdn);
    }

    List<NetworkInterface> networkInterfaces = populateNICs(rhsmFacts);
    if (!networkInterfaces.isEmpty()) {
      facts.setNetworkInterfaces(networkInterfaces);
    }

    List<String> macAddresses = new ArrayList<>();
    rhsmFacts.entrySet().stream()
        .filter(
            entry -> entry.getKey().startsWith(MAC_PREFIX) && entry.getKey().endsWith(MAC_SUFFIX))
        .forEach(
            entry -> {
              List<String> macs = Arrays.asList(entry.getValue().split(COMMA_REGEX));
              macAddresses.addAll(
                  macs.stream()
                      .filter(
                          mac ->
                              mac != null
                                  && !mac.equalsIgnoreCase(NONE)
                                  && !mac.equalsIgnoreCase(UNKNOWN)
                                  && !isTruncated(entry.getKey(), mac))
                      .collect(Collectors.toList()));
            });

    if (!macAddresses.isEmpty()) {
      facts.setMacAddresses(macAddresses);
    }
    extractIpAddresses(rhsmFacts, facts);
  }

  @SuppressWarnings("indentation")
  protected void extractIpAddresses(Map<String, String> rhsmFacts, ConduitFacts facts) {
    Set<String> ipAddresses = new HashSet<>();
    rhsmFacts.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().matches(IP_ADDRESS_FACT_REGEX)
                    && StringUtils.hasLength(entry.getValue()))
        .forEach(
            entry -> {
              List<String> items = Arrays.asList(entry.getValue().split(COMMA_REGEX));
              ipAddresses.addAll(
                  items.stream()
                      .filter(
                          addr ->
                              StringUtils.hasLength(addr)
                                  && !addr.equalsIgnoreCase(UNKNOWN)
                                  && !isTruncated(entry.getKey(), addr))
                      .collect(Collectors.toList()));
            });

    if (!ipAddresses.isEmpty()) {
      facts.setIpAddresses(new ArrayList<>(ipAddresses));
    }
  }

  private List<NetworkInterface> populateNICs(Map<String, String> rhsmFacts) {
    var nicSet = new ArrayList<NetworkInterface>();
    for (Map.Entry<String, String> entry : rhsmFacts.entrySet()) {
      if (entry.getKey().startsWith(MAC_PREFIX) && entry.getKey().endsWith(MAC_SUFFIX)) {
        String[] nicsName = entry.getKey().split(PERIOD_REGEX);
        var mac = entry.getValue();
        var networkInterface = new NetworkInterface();
        networkInterface.setName(nicsName[2]);
        networkInterface.setMacAddress(mac);
        mapInterfaceIps(networkInterface, rhsmFacts, ".ipv4");
        mapInterfaceIps(networkInterface, rhsmFacts, ".ipv6");
        nicSet.add(networkInterface);
      }
    }
    // creates a lo interface if ips exist for it, but no mac was given
    checkLoopbackIPs(nicSet, rhsmFacts);
    return nicSet;
  }

  private void mapInterfaceIps(
      NetworkInterface networkInterface, Map<String, String> facts, String suffix) {
    String prefix = MAC_PREFIX + networkInterface.getName() + suffix;
    String[] ipList;

    if (suffix.equalsIgnoreCase(".ipv4") && facts.containsKey(prefix + "_address_list")) {
      ipList = facts.get(prefix + "_address_list").split(COMMA_REGEX);
      for (String ip : ipList) networkInterface.addIpv4AddressesItem(ip);
    } else if (facts.containsKey(prefix + "_address")) {
      networkInterface.addIpv4AddressesItem(facts.get(prefix + "_address"));
    }

    if (facts.containsKey(prefix + "_address.global_list")) {
      ipList = facts.get(prefix + "_address.global_list").split(COMMA_REGEX);
      for (String ip : ipList) networkInterface.addIpv6AddressesItem(ip);
    } else if (facts.containsKey(prefix + "_address.global")) {
      networkInterface.addIpv6AddressesItem(facts.get(prefix + "_address.global"));
    }

    if (facts.containsKey(prefix + "_address.link_list")) {
      ipList = facts.get(prefix + "_address.link_list").split(COMMA_REGEX);
      for (String ip : ipList) networkInterface.addIpv6AddressesItem(ip);
    } else if (facts.containsKey(prefix + "_address.link")) {
      networkInterface.addIpv6AddressesItem(facts.get(prefix + "_address.link"));
    }
  }

  private void checkLoopbackIPs(
      List<NetworkInterface> networkInterfaces, Map<String, String> facts) {
    boolean loExist = networkInterfaces.stream().anyMatch(nic -> "lo".equals(nic.getName()));
    var lo = new NetworkInterface();

    if (!loExist && facts.containsKey("net.interface.lo.ipv4_address")) {
      lo.setName("lo");
      lo.setMacAddress("00:00:00:00:00:00");
      lo.addIpv4AddressesItem(facts.get("net.interface.lo.ipv4_address"));
      networkInterfaces.add(lo);
    } else if (!loExist && facts.containsKey("net.interface.lo.ipv6_address")) {
      lo.setName("lo");
      lo.setMacAddress("00:00:00:00:00:00");
      lo.addIpv6AddressesItem(facts.get("net.interface.lo.ipv6_address"));
      networkInterfaces.add(lo);
    }
  }

  private void extractVirtualizationFacts(
      Consumer consumer, Map<String, String> rhsmFacts, ConduitFacts facts) {

    String isGuest = rhsmFacts.get(VIRT_IS_GUEST);
    if (StringUtils.hasLength(isGuest) && !isGuest.equalsIgnoreCase(UNKNOWN)) {
      facts.setIsVirtual(isGuest.equalsIgnoreCase(TRUE));
    }

    String vmHost = consumer.getHypervisorName();
    if (StringUtils.hasLength(vmHost)) {
      facts.setVmHost(vmHost);
    }

    String vmHypervisorUuid = consumer.getHypervisorUuid();
    if (StringUtils.hasLength(vmHypervisorUuid)) {
      facts.setVmHostUuid(vmHypervisorUuid);
    }

    String vmId = consumer.getGuestId();
    if (StringUtils.hasLength(vmId)) {
      facts.setGuestId(vmId);
    }
  }

  public void updateInventoryForOrg(String orgId)
      throws MissingAccountNumberException, ApiException {
    updateInventoryForOrg(orgId, null);
  }

  @Timed("rhsm-conduit.sync.org-page")
  public void updateInventoryForOrg(String orgId, String offset)
      throws ApiException, MissingAccountNumberException {

    org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory feedPage =
        rhsmService.getPageOfConsumers(orgId, offset, rhsmService.formattedTime());
    Stream<ConduitFacts> facts = validateConduitFactsForOrg(feedPage);

    long updateSize =
        facts
            .map(
                hostFacts -> {
                  inventoryService.scheduleHostUpdate(hostFacts);
                  return 1;
                })
            .count();
    if (updateSize > 0) {
      inventoryService.flushHostUpdates();
    }
    log.debug(
        "Finished page w/ offset {} of inventory updates for org {}, producing {} updates",
        offset,
        orgId,
        updateSize);
    Optional<String> nextOffset = getNextOffset(feedPage);
    if (nextOffset.isPresent()) {
      log.debug("Queueing up task for next page of org {}", orgId);
      taskManager.updateOrgInventory(orgId, nextOffset.get());
      queueNextPageCounter.increment();
    } else {
      log.info("Host inventory update completed for org {}.", orgId);
      finalizeOrgCounter.increment();
    }
  }

  private Optional<String> getNextOffset(
      org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory feedPage) {
    Pagination pagination = feedPage.getPagination();
    if (pagination != null && pagination.getLimit().equals(pagination.getCount())) {
      return Optional.of(feedPage.getBody().get(pagination.getCount().intValue() - 1).getId());
    }
    return Optional.empty();
  }

  public OrgInventory getInventoryForOrg(String orgId, String offset)
      throws MissingAccountNumberException, ApiException {

    org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory feedPage =
        rhsmService.getPageOfConsumers(orgId, offset, rhsmService.formattedTime());
    return inventoryService.getInventoryForOrgConsumers(
        validateConduitFactsForOrg(feedPage).collect(Collectors.toList()));
  }

  private Stream<ConduitFacts> validateConduitFactsForOrg(
      org.candlepin.subscriptions.conduit.rhsm.client.model.OrgInventory feedPage)
      throws MissingAccountNumberException {

    if (feedPage.getBody().isEmpty()) {
      return Stream.empty();
    }

    // Peek at the first consumer.  If it is missing an account number, that means they all are.
    // Abort
    // and return an empty stream.  No sense in wasting time looping through everything.
    try {
      if (!StringUtils.hasText(feedPage.getBody().get(0).getAccountNumber())) {
        throw new MissingAccountNumberException();
      }
    } catch (NoSuchElementException e) {
      throw new MissingAccountNumberException();
    }

    return feedPage.getBody().stream()
        .map(this::validateConsumer)
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  @SuppressWarnings("indentation")
  private Optional<ConduitFacts> validateConsumer(Consumer consumer) {
    try {
      if (consumer.getType() != null && IGNORED_CONSUMER_TYPES.contains(consumer.getType())) {
        return Optional.empty();
      }
      ConduitFacts facts = transformHostTimer.recordCallable(() -> getFactsFromConsumer(consumer));
      facts.setAccountNumber(consumer.getAccountNumber());

      Set<ConstraintViolation<ConduitFacts>> violations =
          validateHostTimer.recordCallable(() -> validator.validate(facts));
      if (violations.isEmpty()) {
        return Optional.of(facts);
      } else {
        if (log.isInfoEnabled()) {
          log.info(
              "Consumer {} failed validation: {}",
              consumer.getName(),
              violations.stream()
                  .map(this::buildValidationMessage)
                  .collect(Collectors.joining("; ")));
        }
        return Optional.empty();
      }
    } catch (Exception e) {
      log.warn(String.format("Skipping consumer %s due to exception", consumer.getUuid()), e);
      return Optional.empty();
    }
  }

  private String buildValidationMessage(ConstraintViolation<ConduitFacts> x) {
    return String.format("%s: %s: %s", x.getPropertyPath(), x.getMessage(), x.getInvalidValue());
  }

  private boolean isTruncated(String factKey, String toCheck) {
    if (toCheck != null && toCheck.endsWith("...")) {
      log.info("Consumer fact value was truncated. Skipping value: {}:{}", factKey, toCheck);
      return true;
    }
    return false;
  }
}
