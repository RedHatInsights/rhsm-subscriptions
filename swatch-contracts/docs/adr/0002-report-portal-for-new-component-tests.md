### 0002 - Use Report Portal Instead of Ibutsu for Test Reporting

* **Status:** Superseded by [0005](0005-ephemeral-test-results-via-s3-for-ibutsu.md)
* **Deciders:**
* **Date:** 2025-09-22

> **Note:** This record is kept for context only. [0005](0005-ephemeral-test-results-via-s3-for-ibutsu.md) replaces it: Report Portal and the Data Router are being decommissioned, so SWATCH publishes the test results to **S3** for Ibutsu import instead.

---

### Context

We want to continue collecting and sharing automated test results while moving to the **external Konflux cluster**.  
However, the external Konflux cluster cannot directly reach **Ibutsu**, our current test reporting solution.  
It *can*, however, reach an external mirror of **Report Portal**.

The alternative—using the **internal Konflux cluster**—would require making our repositories private, which we prefer to avoid.

To address these constraints and reduce maintenance burden, we are switching from **Ibutsu** to **Report Portal** for component test reporting in ephemeral environments.  
The Konflux-supported [Data Router Task](https://datarouter.ccitredhat.com/) provides a straightforward way to upload component test reports to Report Portal without custom scripting.

Reference: [Konflux announcement](https://groups.google.com/a/redhat.com/g/konflux-announce/c/1PPcMNY9u3M/m/tMByhntgDAAJ)

---

### Decision

We will **use Report Portal instead of Ibutsu** as the primary test reporting platform **for component tests running in ephemeral environments**.  
These component tests will upload their results to the external mirror of Report Portal using the officially supported **Data Router Task**.

Our **integration, long-run, and IQE tests will continue to report to Ibutsu**, but they already report to **Report Portal** as well, so they retain their existing redundancy.

---

### Trade-offs

* **Positive:**
    * **External Cluster Compatibility:** Enables seamless test report uploads from the external Konflux cluster where Ibutsu is unreachable.
    * **Reduced Maintenance:** Removes the need for custom scripts or workarounds to reach Ibutsu.
    * **Supported Path:** Leverages an officially supported Konflux solution, improving long-term reliability.
    * **Simplified Access:** Avoids the need to make repositories private for internal Konflux usage.

* **Negative:**
    * **Migration Effort:** Requires updating pipelines and documentation to integrate with Report Portal for component tests.
    * **Loss of Redundancy for Component Tests:** Unlike integration/long-run/IQE tests, component tests will no longer have Ibutsu as a secondary reporting target.
    * **Dependency on Konflux Team:** Continued reliance on the Konflux Data Router Task and the external Report Portal mirror for ongoing support.
