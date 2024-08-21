Place your mapping JSON files in this directory.

See the [WireMock documentation](https://wiremock.org/docs/stubbing/) for
information about the format.

The [WireMock admin
API](https://wiremock.org/docs/standalone/admin-api-reference/) is very handy to
reference as well.

Starting a WireMock server using mappings in this directory is as easy as

```
podman run -it --rm -p 8101:8080 --name wiremock -v $(git rev-parse --show-toplevel)/stub:/home/wiremock:z wiremock/wiremock:2.32.0 --verbose
```
