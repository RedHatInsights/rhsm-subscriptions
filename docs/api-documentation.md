# API Documentation

Customer-facing API documentation on the Hybrid Cloud Console:

| Version | Stage | Production |
|---------|-------|------------|
| **v1** | [Swagger UI](https://console.stage.redhat.com/docs/api/rhsm-subscriptions/v1) | [Swagger UI](https://console.redhat.com/docs/api/rhsm-subscriptions/v1) |
| **v2** | [Swagger UI](https://console.stage.redhat.com/docs/api/rhsm-subscriptions/v2) | [Swagger UI](https://console.redhat.com/docs/api/rhsm-subscriptions/v2) |

OpenAPI JSON (no console login): `https://{console-host}/api/rhsm-subscriptions/v{1,2}/openapi.json`

The public v1/v2 specs document the full customer API surface, including routes
implemented by other services (for example capacity and subscription SKU reports
in swatch-contracts, and utilization org-preferences in swatch-utilization) and
proxied by swatch-api nginx.

Our API is also listed on the [Red Hat API
catalog](https://developers.redhat.com/api-catalog/). That site is built by
[api-documentation-frontend](https://github.com/RedHatInsights/api-documentation-frontend),
which registers RHSM in
[Discovery.yml](https://github.com/RedHatInsights/api-documentation-frontend/blob/main/packages/discovery/Discovery.yml)
and pulls the live spec from
`https://console.redhat.com/api/rhsm-subscriptions/v{1,2}/openapi.json` at
build time.

Locally, the API is served from `/api-docs/` for Spring and
`/q/dev-ui/io.quarkus.quarkus-smallrye-openapi/swagger-ui` for Quarkus.
