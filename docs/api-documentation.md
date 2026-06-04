# API Documentation

Our API is documented in the [Red Hat API
catalog](https://developers.redhat.com/api-catalog/).  The information there
comes from our OpenAPI specification.

The API catalog has
[code](https://github.com/RedHatInsights/api-documentation-frontend) to
transform a [YAML
document](https://github.com/RedHatInsights/api-documentation-frontend/blob/main/packages/discovery/Discovery.yml)
into the displayed HTML.

Locally, the API is served from `/api-docs/` for Spring and
`/q/dev-ui/io.quarkus.quarkus-smallrye-openapi/swagger-ui` for Quarkus.
