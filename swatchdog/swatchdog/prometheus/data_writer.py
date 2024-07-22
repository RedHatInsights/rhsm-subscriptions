import datetime
import random
import typing as t
from collections import namedtuple

Metric = namedtuple("Metric", ["metric", "lower_bound", "upper_bound"])

# NB: This class is in its own module with minimal dependencies so that IQE can import the module
# easily without dragging in all the dependencies that swatchdog uses.


class MockPrometheusWriter:
    def __init__(
        self,
        *,
        cluster_id: str,
        billing_provider: str,
        metrics: t.Tuple[t.Tuple[str, int, int]],
        product: str,
        marketplace_account: str,
        account: str,
        org: str,
        prometheus_format: bool,
        start_time: datetime.datetime,
        end_time: datetime.datetime,
    ):
        self.cluster_id = cluster_id
        self.billing_provider = billing_provider
        self.product = product
        self.marketplace_account = marketplace_account
        self.account = account
        self.org = org
        self.start_time = start_time
        self.end_time = end_time

        # Prometheus uses Unix epoch milliseconds as a signed integer
        # OpenMetrics uses Unix epoch seconds as a float (with optional nanoseconds part)
        # See https://github.com/prometheus/prometheus/discussions/9661

        self.prometheus_format = prometheus_format
        self.metrics = [Metric(*x) for x in metrics]

    def generate(self) -> str:
        output = [
            "# HELP ocm_subscription placeholder",
            "# TYPE ocm_subscription counter",
        ]

        for m in self.metrics:
            output.append(f"# HELP {m.metric} placeholder")
            output.append(f"# TYPE {m.metric} counter")

        cursor = self.start_time

        while cursor < self.end_time:
            time = int(cursor.timestamp())
            if self.prometheus_format:
                # Convert to milliseconds
                time *= 1000

            output.append(
                "ocm_subscription{"
                + f'_id="{self.cluster_id}",'
                + 'billing_model="marketplace",'
                + f'ebs_account="{self.account}",'
                + f'external_organization="{self.org}",'
                + 'support="Premium",'
                + f'billing_marketplace="{self.billing_provider}",'
                + f'billing_marketplace_account="{self.marketplace_account}",'
                + f'product="{self.product}"'
                + "} "
                + f"1.0 {time}"
            )
            for m in self.metrics:
                value = random.randint(m.lower_bound, m.upper_bound)
                output.append(
                    m.metric
                    + "{"
                    + f'_id="{self.cluster_id}"'
                    + "} "
                    + f"{value} {time}"
                )
            cursor += datetime.timedelta(minutes=5)
        output.append("# EOF")
        output.append("\n")
        return "\n".join(output)
