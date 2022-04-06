## Grafana Dashboards

See App-SRE documentation on updating dashboards for more info.

Essentially:

1. Edit the dashboard on the stage grafana instance.
2. Export the dashboard, choosing to "export for sharing externally", save JSON to a file.
3. Rename the file to `subscription-watch.json`.

Use the following command to update the configmap YAML:

```
oc create configmap grafana-dashboard-subscription-watch --from-file=subscription-watch.json -o yaml --dry-run=client > ./grafana-dashboard-subscription-watch.configmap.yaml
cat << EOF >> ./grafana-dashboard-subscription-watch.configmap.yaml
  annotations:
    grafana-folder: /grafana-dashboard-definitions/Insights
  labels:
    grafana_dashboard: "true"
EOF
```

Possibly useful, to extract the JSON from the k8s configmap file:

```
oc extract -f dashboards/grafana-dashboard-subscription-watch.configmap.yaml --confirm
```
