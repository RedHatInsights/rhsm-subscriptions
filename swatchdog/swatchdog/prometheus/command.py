import datetime
import os
import re
import click
import yaml
import json

from rich.text import Text
from urllib.parse import urlencode
from itertools import groupby

from .data_writer import MockPrometheusWriter
from .. import console, notice


class PromQLWriter:
    def __init__(self, templates: dict, product: str, metric: str, org: str, url: str):
        self.templates = templates
        self.product = product.lower() if product else None
        self.metric = metric.lower() if metric else None
        self.org = org
        self.url = url
        self.seen_products = set()

    def process_yaml(self, product_yaml: dict):
        metrics = list(
            filter(lambda x: x.get("prometheus"), product_yaml.get("metrics", []))
        )
        product_id = product_yaml["id"]
        if len(metrics) == 0:
            return
        if self.product and self.product != product_id.lower():
            return

        output = self.build_account_query(metrics)
        output.append(self.build_metrics(metrics))

        if output:
            console.rule(f"[yellow]{product_id}", align="left")
            console.print(output, highlight=False, end="")

    def build_account_query(self, metrics: list):
        output = Text()
        account_queries = {}
        for m in metrics:
            prometheus_config = m["prometheus"]
            query_key = prometheus_config["queryKey"]
            query_params = prometheus_config["queryParams"]
            default = self.templates["account_query"]["default"]
            account_promql = self.template_promql(
                self.templates["account_query"].get(query_key, default), query_params
            )
            account_queries[m["id"]] = account_promql

        # account_queries looks like {'Cores': 'promql1', 'Instance-hours': 'promql1', 'Something-else': 'promql2'}
        g = groupby(
            sorted(account_queries, key=account_queries.get), key=account_queries.get
        )
        # query map looks like {('Cores', 'Instance-hours'): 'promql1', ('Something-else',): 'promql2'}
        query_map = {tuple(v): k for (k, v) in g}

        for k, v in query_map.items():
            if not self.metric or self.metric in map(lambda x: x.lower(), k):
                if self.url:
                    output.append(
                        f"Accounts for metrics {', '.join(k)}:\n", style="green"
                    )
                    output.append(f"{self.grafana_url(v)}\n\n")
                else:
                    output.append(
                        f"Accounts PromQL for metrics {', '.join(k)}:\n", style="green"
                    )
                    output.append(f"{v}\n\n")

        return output

    def build_metrics(self, metrics: list):
        output = Text()

        # Remove metrics we don't care about
        if self.metric:
            metrics = filter(lambda x: self.metric == x["id"].lower(), metrics)

        for m in metrics:
            prometheus_config = m["prometheus"]
            query_key = prometheus_config["queryKey"]
            query_params = prometheus_config["queryParams"]
            default = self.templates["query"]["default"]
            metric_promql = self.template_promql(
                self.templates["query"].get(query_key, default), query_params
            )
            if self.url:
                output.append(f"{m['id']}:\n", style="green")
                output.append(f"{self.grafana_url(metric_promql)}\n\n")
            else:
                output.append(f"{m['id']} PromQL:\n", style="green")
                output.append(f"{metric_promql}\n\n")
        return output

    def template_promql(self, template: str, query_params: dict):
        while "metric.prometheus.queryParams" in template:
            regex = r"\#\{metric.prometheus.queryParams\[([^]]+)\]\}"
            param = re.search(regex, template).group(1)
            template = re.sub(regex, query_params.get(param, ""), template, 1)
        if self.org:
            template = template.replace(r'="#{runtime[orgId]}"', f'="{self.org}"')
        else:
            template = template.replace(r'="#{runtime[orgId]}"', '=~".*"')
        return template

    def grafana_url(self, promql_expr: str):
        grafana_params = urlencode(
            [
                (
                    "left",
                    json.dumps(
                        {
                            "queries": [
                                {
                                    "refId": "A",
                                    "editorMode": "code",
                                    "expr": promql_expr,
                                    "legendFormat": "__auto",
                                    "range": True,
                                    "instant": True,
                                    "interval": "3600",
                                }
                            ],
                            "range": {"from": "now-12h", "to": "now"},
                        }
                    ),
                )
            ]
        )

        return f"{self.url}/explore?orgId=1&" + grafana_params


@click.group
def prometheus():
    """Create a namespace for prometheus related commands"""
    pass


def validate_path(ctx, param, value):
    path = os.path.join(ctx.obj["project_root"], value)
    if not os.path.exists(path):
        raise click.BadParameter(f"{path} does not exist")
    return path


def validate_metric(ctx, param, value):
    errors = []
    for x in value:
        if x[1] > x[2]:
            errors.append(f"Error with metric {x[0]}: {x[1]} is greater than {x[2]}")
    if errors:
        raise click.BadParameter("\n".join(errors))
    return value


def convert_date(ctx, param, value):
    try:
        return datetime.datetime.fromisoformat(value)
    except ValueError:
        raise click.BadParameter(f'Could not parse "{value}". Use ISO 8601 format.')


@prometheus.command()
@click.option(
    "--source",
    default="swatch-metrics/src/main/resources/application.yaml",
    callback=validate_path,
    type=str,
    show_default=True,
    help="Source for the PromQL templates relative to the project root",
)
@click.option(
    "--product-config-dir",
    default="swatch-product-configuration/src/main/resources/subscription_configs",
    callback=validate_path,
    type=str,
    show_default=True,
    help="Product configuration directory relative to the project root",
)
@click.option(
    "--url",
    type=str,
    help="Output links with Grafana base url (e.g. https://grafana.example.com)",
)
@click.option("--product", type=str, help="Limit to a specific product (e.g. rosa)")
@click.option("--metric", type=str, help="Limit to a specific metric")
@click.option("--org", type=str, help="Limit to a specific org")
def promql(
    source: str, product_config_dir: str, url: str, product: str, metric: str, org: str
):
    with open(source) as config_file:
        config = yaml.safe_load(config_file)
        metric_section = config["rhsm-subscriptions"]["metering"]["prometheus"][
            "metric"
        ]
        query_templates = metric_section["queryTemplates"]
        account_query_templates = metric_section["accountQueryTemplates"]

    templates = {"account_query": account_query_templates, "query": query_templates}

    for root, dirs, files in os.walk(product_config_dir):
        files = filter(lambda x: "basilisk" not in x, files)
        for name in files:
            with open(os.path.join(root, name)) as product_config_file:
                config = yaml.safe_load(product_config_file)
                writer = PromQLWriter(templates, product, metric, org, url)
                writer.process_yaml(config)


@prometheus.command()
@click.option("--cluster-id", default="test01", type=str)
@click.option("--billing-provider", default="aws", type=str)
@click.option(
    "--metric",
    "metrics",
    default=[
        (
            "kafka_id:kafka_broker_quota_totalstorageusedbytes:max_over_time1h_gibibyte_months",
            1,
            100,
        )
    ],
    multiple=True,
    metavar="METRIC LOWER_BOUND UPPER_BOUND",
    type=(str, int, int),
    show_default=True,
    callback=validate_metric,
    help="Metric to use followed by the upper bound and lower bound to use for generated values. May be given "
    "multiple times",
)
@click.option("--product", default="rosa", type=str, show_default=True)
@click.option("--marketplace-account", default="mktp-123", type=str, show_default=True)
@click.option("--account", default="account123", type=str, show_default=True)
@click.option("--org", default="org123", type=str, show_default=True)
@click.option(
    "--prometheus/--openmetrics",
    default=True,
    type=bool,
    show_default=True,
    help="Use Prometheus format (epoch as an int with millisecond precision) or Openmetrics format (Unix "
    "epoch as a float with nano precision).  The `promtool push metrics` command uses Prometheus "
    "format while the `promtool tsdb create-blocks-from openmetrics` command requires openmetrics format.",
)
@click.option("--file", type=str)
@click.option(
    "--start-time",
    default=(datetime.datetime.now() - datetime.timedelta(days=1)).isoformat(),
    show_default="24 hours ago",
    type=str,
    callback=convert_date,
    help="Start time in ISO 8601 datetime format",
)
@click.option(
    "--end-time",
    default=datetime.datetime.now().isoformat(),
    show_default="current time",
    type=str,
    callback=convert_date,
    help="End time in ISO 8601 datetime format",
)
def mock_data(
    cluster_id: str,
    billing_provider: str,
    metrics: tuple[tuple[str, int, int]],
    product: str,
    marketplace_account: str,
    account: str,
    org: str,
    prometheus: bool,
    file: str,
    start_time: datetime.datetime,
    end_time: datetime.datetime,
):
    if file:
        notice(
            f"Generating mock data for _id={cluster_id}, product={product}, metrics={metrics}"
        )

    if start_time > end_time:
        raise click.BadOptionUsage(
            "start-time", "--start-time must be before -end-time"
        )

    writer = MockPrometheusWriter(
        cluster_id=cluster_id,
        billing_provider=billing_provider,
        metrics=metrics,
        product=product,
        marketplace_account=marketplace_account,
        account=account,
        org=org,
        prometheus_format=prometheus,
        start_time=start_time,
        end_time=end_time,
    )

    output = writer.generate()
    if file:
        with click.open_file(file, "w") as f:
            f.write(output)
    else:
        console.print(output, highlight=False)
