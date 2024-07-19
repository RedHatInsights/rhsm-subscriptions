import logging
import os
import re
import typing as t
import click
import yaml
import json

from urllib.parse import urlencode

from .. import SwatchContext, SwatchDogError
from .. import console, info, err, invoke_config, pass_swatch


class PromQLWriter:
    def __init__(self):
        pass


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
    "--product-config",
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
    source: str, product_config: str, url: str, product: str, metric: str, org: str
):
    with open(source) as config_file:
        config = yaml.safe_load(config_file)
        query_templates = config["rhsm-subscriptions"]["metering"]["prometheus"]["metric"][
            "queryTemplates"
        ]
        account_query_templates = config["rhsm-subscriptions"]["metering"]["prometheus"][
            "metric"
        ]["accountQueryTemplates"]

    for root, dirs, files in os.walk(product_config):
        seen_products = set()
        for name in files:
            if "basilisk" in name:
                continue
            with open(os.path.join(root, name)) as config_file:
                config = yaml.safe_load(config_file)
                metrics = config.get("metrics", [])
                if len(list(filter(lambda m: m.get("prometheus"), metrics))) == 0:
                    continue
                if product and product.lower() != config["id"].lower():
                    continue
                for m in metrics:
                    prometheus_config = m.get("prometheus")
                    if metric and metric.lower() != m["id"].lower():
                        continue
                    if prometheus_config:
                        query_key = prometheus_config["queryKey"]
                        query_params = prometheus_config["queryParams"]
                        account_promql = template_promql(
                            account_query_templates.get(
                                query_key, account_query_templates["default"]
                            ),
                            query_params,
                            org
                        )
                        metric_promql = template_promql(query_templates.get(query_key), query_params, org)
                        if config["id"] not in seen_products:
                            print()
                            print(config["id"])
                            print("-" * len(config["id"]))
                            seen_products.add(config["id"])
                            if not metric:
                                if url:
                                    print(f"  Accounts: {grafana_url(account_promql, url)}")
                                else:
                                    print(f"  Accounts PromQL: {account_promql}")
                        if url:
                            print(f'  {m["id"]}: {grafana_url(metric_promql, url)}')
                        else:
                            print(f'  {m["id"]} PromQL: {metric_promql}')


def template_promql(template, query_params, org):
    if template.startswith("${OPENSHIFT_ENABLED_ACCOUNT_PROMQL"):
        template = template.lstrip("${OPENSHIFT_ENABLED_ACCOUNT_PROMQL:")
        template = template.rstrip("}")
    while "metric.prometheus.queryParams" in template:
        regex = r"\#\{metric.prometheus.queryParams\[([^]]+)\]\}"
        param = re.search(regex, template).group(1)
        template = re.sub(regex, query_params.get(param, ""), template, 1)
    if org:
        template = template.replace(r'="#{runtime[orgId]}"', f'="{org}"')
    else:
        template = template.replace(r'="#{runtime[orgId]}"', '=~".*"')
    return template


def grafana_url(promql, url):
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
                                "expr": promql,
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

    return f"{url}/explore?orgId=1&" + grafana_params
