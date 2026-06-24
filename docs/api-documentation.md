# API Documentation

Customer-facing API documentation on the Hybrid Cloud Console:

| Version | Stage | Production |
|---------|-------|------------|
| **v2** | [Swagger UI](https://console.stage.redhat.com/docs/api/rhsm-subscriptions/v2) | [Swagger UI](https://console.redhat.com/docs/api/rhsm-subscriptions/v2) (when v2 is deployed) |
| **v1** | [Swagger UI](https://console.stage.redhat.com/docs/api/rhsm-subscriptions/v1) | [Swagger UI](https://console.redhat.com/docs/api/rhsm-subscriptions/v1) |

OpenAPI JSON (no console login): `https://{console-host}/api/rhsm-subscriptions/v{1,2}/openapi.json`

Our API is also listed in the [Red Hat API
catalog](https://developers.redhat.com/api-catalog/). The catalog uses our
OpenAPI specification via
[api-documentation-frontend](https://github.com/RedHatInsights/api-documentation-frontend)
and [Discovery.yml](https://github.com/RedHatInsights/api-documentation-frontend/blob/main/packages/discovery/Discovery.yml).

Locally, the API is served from `/api-docs/` for Spring and
`/q/dev-ui/io.quarkus.quarkus-smallrye-openapi/swagger-ui` for Quarkus.
