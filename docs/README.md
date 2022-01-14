# Subscription watch

## Summary

Subscription watch can be thought of as several services that provide related functionality:

* [system-conduit](#system-conduit)
* [metrics ingress](#metrics-ingress)
* [tally](#tally)
* [subscription sync](#subscription-sync)
* [API/UI](#apiui)
* [billing usage notification](#billing-producers)

Also, see [networking diagrams](#networking-diagrams) to understand how requests are routed.

![Context diagram for Subscription watch](context.svg)

## System conduit

Service that syncs system data from Hosted Candlepin into HBI.

![Container diagram for system conduit](container-system-conduit.svg)

## Metrics Ingress

Services that sync system/instance telemetry data into Subscription watch.

![Container diagram for Metrics Ingress](container-metrics-ingress.svg)

## Tally

Service that tallies system usage based on telemetry data from various sources.

![Container diagram for Tally](container-tally.svg)

## Subscription Sync

Service that syncs subscription/offering data from RH IT services.

![Container diagram for Subscription Sync](container-subscription-sync.svg)

## API/UI

Customer facing views of the usage and capacity data.

![Container diagram for API/UI](container-ui.svg)

## Billing Producers

Services that notify billing services of hourly usage.

![Container diagram for Billing Producers](container-billing.svg)

## Networking diagrams

### Customer-facing API

![Networking diagram for customer API](networking-public-api.svg)

### Internal API

![Networking diagram for internal APIs](networking-jolokia-api.svg)
