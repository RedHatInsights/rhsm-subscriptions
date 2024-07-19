import os
import re
import typing as t
import click
import yaml
import json

from urllib.parse import urlencode
from .. import console


class PromQLWriter:
    def __init__(
        self, templates: t.Dict, product: str, metric: str, org: str, url: str
    ):
        self.templates = templates
        self.product = product
        self.metric = metric
        self.org = org
        self.url = url
        self.seen_products = set()

    def process_yaml(self, product_yaml: t.Dict):
        config_metrics = product_yaml.get("metrics", [])
        prometheus_enabled = list(filter(lambda x: x.get("prometheus"), config_metrics))
        if len(prometheus_enabled) == 0:
            return
        if self.product and self.product.lower() != product_yaml["id"].lower():
            return
        self.build_metrics(
            product_yaml["id"],
            config_metrics,
        )

    def build_metrics(self, product_id: str, config_metrics: t.List):
        # Remove metrics we don't care about
        if self.metric:
            config_metrics = filter(
                lambda x: self.metric.lower() == x["id"].lower(), config_metrics
            )
        # Ensure a prometheus section
        config_metrics = filter(lambda x: x["prometheus"], config_metrics)

        for m in config_metrics:
            prometheus_config = m["prometheus"]
            query_key = prometheus_config["queryKey"]
            query_params = prometheus_config["queryParams"]
            default = self.templates["account_query"]["default"]
            account_promql = self.template_promql(
                self.templates["account_query"].get(query_key, default), query_params
            )
            metric_promql = self.template_promql(
                self.templates["query"][query_key], query_params
            )
            if product_id not in self.seen_products:
                console.rule(f"[yellow]{product_id}", align="left")
                self.seen_products.add(product_id)
                if not self.metric:
                    if self.url:
                        console.print("[green]Accounts:")
                        console.print(
                            f"{self.grafana_url(account_promql)}\n", highlight=False
                        )
                    else:
                        console.print("[green]Accounts PromQL:")
                        console.print(f"{account_promql}\n", highlight=False)
            if self.url:
                console.print(f"[green]{m['id']}:")
                console.print(f"{self.grafana_url(metric_promql)}\n", highlight=False)
            else:
                console.print(f"[green]{m['id']} PromQL:")
                console.print(f"{metric_promql}\n", highlight=False)

    def template_promql(self, template: str, query_params: t.Dict):
        if template.startswith("${OPENSHIFT_ENABLED_ACCOUNT_PROMQL"):
            template = template.lstrip("${OPENSHIFT_ENABLED_ACCOUNT_PROMQL:")
            template = template.rstrip("}")
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


@prometheus.command()
@click.option(
    "--source",
    default="swatch-metrics/src/main/resources/application.yaml",
    callback=validate_path,
    type=str,
    help="Source for the PromQL templates relative to the project root",
)
@click.option(
    "--product-config-dir",
    default="swatch-product-configuration/src/main/resources/subscription_configs",
    callback=validate_path,
    type=str,
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
    writer = PromQLWriter(templates, product, metric, org, url)

    for root, dirs, files in os.walk(product_config_dir):
        files = filter(lambda x: "basilisk" not in x, files)
        for name in files:
            with open(os.path.join(root, name)) as product_config_file:
                config = yaml.safe_load(product_config_file)
                writer.process_yaml(config)
