# Usage

There are two web interfaces.  Once at http://localhost:4242 and one at
http://localhost:4242/admin.  The later is where API tokens can be issued.  You
can log into the web UI with the user `admin` and `unleash4all` as the password.

The container image starts with two tokens.  Two tokens are created in our
Docker compose: an admin token, `*:*.unleash-insecure-admin-api-token`, for
admin operations and a client token,
`default:development.unleash-insecure-api-token`, for client applications to
use.  The `default` in a token refers to the project and the `development`
refers to the environment.  Thus, the client token is scoped.  The admin token
uses wildcards and is not scoped to a project or environment.

The REST API is [documented](https://docs.getunleash.io/reference/api/unleash)
extensively, but the most important operation is creating a simple flag.  
You can use the Web UI which is fairly self-explanatory (with the help of the
documentation).  There is also a very useful feature where the web UI
automatically generates a corresponding `curl` command.  Here's the command
to create a flag.

```bash
curl --location --request POST 'http://localhost:4242/api/admin/projects/default/features' \
    --header 'Authorization: *:*.unleash-insecure-admin-api-token' \
    --header 'Content-Type: application/json' \
    --data-raw '{
  "type": "release",
  "name": "greetingFlag",
  "description": "",
  "impressionData": false
}'
```

# Client Usage
There are a couple ways of switching on the feature flag in Quarkus and Spring.
The most direct is to inject an Unleash object.  This object is auto-configured
by Spring and Quarkus, so all you need to do is make sure the configuration
properties are present (like the auth token and server URL).  For example,

```yaml
"%dev":
  quarkus:
    unleash:
      application: swatch-contracts
      instance-id: swatch-contracts
      environment: development
      url: http://localhost:4242/api
      token: default:development.unleash-insecure-api-token
```

for Quarkus or

```yaml
io:
  getunleash:
    app-name: rhsm
    instance-id: rhsm
    environment: development
    api-url: http://localhost:4242/api
    api-token: default:development.unleash-insecure-api-token
```

for Spring.

The Unleash object provides multiple methods to interrogate the feature flags
but the most basic is `Unleash.isEnabled(java.lang.String)`.  There are a
[number](https://docs.getunleash.io/reference/sdks/java) of other more advanced
operations for handling flags that should only apply to clients with certain
properties (e.g. user agent or geographical region) or for subscribing to a
service to receive notification when Unleash updates internally.  There is also
a `FakeUnleash` class that is useful for unit testing.

The other possibility for using Unleash is an annotation based approach.  This
requires an interface and two implementations.  A method signature in the
interface is tagged with the `@Toggle` annotation and given the flag name and
the name of an bean for the alternate implementation.  The primary
implementation is injected into the class (using a `@Qualified` annotation since
there are two declared beans for the same interface).  If the flag is active,
Unleash has an AOP aspect that dynamically replaces the primary implementation
with the secondary.  This approach is convenient but offers less fine-grained
control, especially since the `@Toggle` annotation can only be applied to a
method and not to an entire class instance.

For Quarkus, this type of annotation based approach doesn't exist.  There is a
shortcut to define a class level boolean based on a flags value, but that is
just very minor syntactic sugar for `Unleash.isEnabled()`.

# QE and Integration Considerations

An Unleash server can be added to an ephemeral environment by requesting it in
the
[ClowdEnv](https://consoledot.pages.redhat.com/clowder/dev/api_reference.html#k8s-api-github-com-redhatinsights-clowder-apis-cloud-redhat-com-v1alpha1-featureflagsconfig) like so

```yaml
apiVersion: cloud.redhat.com/v1alpha1
kind: ClowdEnvironment
metadata:
  name: myenv
spec:
  # Other Env Config
  providers:
    featureFlags:
      mode: local
      pvc: false
```

The `ClowdApp` also needs to enable feature flags

```yaml
apiVersion: cloud.redhat.com/v1alpha1
kind: ClowdApp
metadata:
  name: myapp
spec:
  featureFlags: true
```

The hostname, port, and access token will then be exposed in the
`cdappconfig.json` file like so

```json
{
  "featureFlags": {
    "hostname": "ff-server.server.example.com",
    "port": 4242
    "clientAccessToken": "someaccesstoken"
  }
}
```

Creating and manipulating the flags can then be handled via `oc port-forward`
and `curl`.  In the case of IQE testing, IQE has very recently (as of 6 May
2024) added [Unleash
support](https://insights-qe.pages.redhat.com/iqe-core-docs/feature_flags.html)
to interrogate the value of flags.  The flags still must be created in an
out-of-band fashion either through the web UI or through `oc port-forward` and
`curl`.

# Populating Unleash - Potential Solutions

Unleash will accept a bulk import of flags or one-off creation.  Oddly, bulk
import of flags requires a different type of API token from the admin token.
Bulk importing via
[`/api/admin/features-batch/import`](https://docs.getunleash.io/reference/api/unleash/import-toggles)
requires a personal access token for the account.

Creating a personal access token should be a simple matter of an API call:
```
curl --location --request POST 'http://localhost:4242/api/admin/user/tokens' \
    --header 'Authorization: *:*.unleash-insecure-admin-api-token' \
    --header 'Content-Type: application/json' \
    --data-raw '{
  "description": "Personal Admin Token",
  "impressionData": "3024-05-07T00:00:00Z"
}'
```

Unfortunately, there seems to be a bug with this API in version 5.8.2 which is
the version of Unleash that we have available.  When invoking it, Unleash
returns an error `Undefined binding(s) detected for keys [0] when compiling RAW
query`.  That leaves the only the web UI as a method of creating a personal
access token which is a non-starter for scripting purposes.

If some suitable means of creating a personal access token is found, the bulk
import process seems to cooperate fairly well with existing configuration.
Unleash looks for any conflicts between the import and the existing
configuration and rejects the import.  There are some
[caveats](https://docs.getunleash.io/how-to/how-to-environment-import-export#import-warnings)
to this, however, so read the fine print.

The alternative is to create feature flags one at a time via
[`/api/admin/projects/:projectId/features`](https://docs.getunleash.io/reference/api/unleash/create-feature)
which does accept a admin API token.
