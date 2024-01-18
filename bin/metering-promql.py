#!/usr/bin/env python

import argparse
import json
import os
import re
import sys
from urllib.parse import urlencode

try:
    import yaml
except ImportError:
    print("python3-pyyaml missing. dnf install python3-pyyaml or pip install PyYAML")
    sys.exit(1)

parser = argparse.ArgumentParser(description="Output PromQL for debugging")
parser.add_argument(
    "--url",
    help="Output links with Grafana base url (e.g. https://grafana.example.com )",
)
parser.add_argument("--product", help="Limit to a specific product (e.g. rosa)")
parser.add_argument("--metric", help="Limit to a specific metric")
parser.add_argument("--org", help="Limit metric queries to a specific org")
args = parser.parse_args()

with open(
    "swatch-metrics/src/main/resources/application.yaml"
) as config_file:
    config = yaml.safe_load(config_file)
    query_templates = config["rhsm-subscriptions"]["metering"]["prometheus"]["metric"][
        "queryTemplates"
    ]
    account_query_templates = config["rhsm-subscriptions"]["metering"]["prometheus"][
        "metric"
    ]["accountQueryTemplates"]


def promql(template, query_params):
    if template.startswith("${OPENSHIFT_ENABLED_ACCOUNT_PROMQL"):
        template = template.lstrip("${OPENSHIFT_ENABLED_ACCOUNT_PROMQL:")
        template = template.rstrip("}")
    while "metric.prometheus.queryParams" in template:
        regex = r"\#\{metric.prometheus.queryParams\[([^]]+)\]\}"
        param = re.search(regex, template).group(1)
        template = re.sub(regex, query_params.get(param, ""), template, 1)
    if args.org:
        template = template.replace(r'="#{runtime[orgId]}"', f'="{args.org}"')
    else:
        template = template.replace(r'="#{runtime[orgId]}"', '=~".*"')
    return template


def grafana_url(promql):
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

    return f"{args.url}/explore?orgId=1&" + grafana_params


for root, dirs, files in os.walk(
    "swatch-product-configuration/src/main/resources/subscription_configs"
):
    seen_products = set()
    for name in files:
        if "basilisk" in name:
            continue
        with open(os.path.join(root, name)) as config_file:
            config = yaml.safe_load(config_file)
            metrics = config.get("metrics", [])
            if len(list(filter(lambda m: m.get("prometheus"), metrics))) == 0:
                continue
            if args.product and args.product.lower() != config["id"].lower():
                continue
            for metric in metrics:
                prometheus_config = metric.get("prometheus")
                if args.metric and args.metric.lower() != metric["id"].lower():
                    continue
                if prometheus_config:
                    query_key = prometheus_config["queryKey"]
                    query_params = prometheus_config["queryParams"]
                    account_promql = promql(
                        account_query_templates.get(
                            query_key, account_query_templates["default"]
                        ),
                        query_params,
                    )
                    metric_promql = promql(query_templates.get(query_key), query_params)
                    if config["id"] not in seen_products:
                        print()
                        print(config["id"])
                        print("-" * len(config["id"]))
                        seen_products.add(config["id"])
                        if not args.metric:
                            if args.url:
                                print(f"  Accounts: {grafana_url(account_promql)}")
                            else:
                                print(f"  Accounts PromQL: {account_promql}")
                    if args.url:
                        print(f'  {metric["id"]}: {grafana_url(metric_promql)}')
                    else:
                        print(f'  {metric["id"]} PromQL: {metric_promql}')
