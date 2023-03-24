Mock AWS Endpoints
==================

Using [moto](http://docs.getmoto.org/en/latest/docs/server_mode.html) and nginx together, this
allows testing of select mocked AWS services (optionally TLS-enabled).

Without TLS
-----------

Run only the moto service:

```shell
podman-compose up -d moto
```

Override the endpoints for any AWS services you wish to mock to
http://localhost:5000.

With TLS
--------
First generate the necessary certificates:

```shell
./generate-certs.sh
```

Then start via podman-compose:

```shell
podman-compose up -d
```

Run in another shell (or background) this command which forwards traffic on port 443 to 8443:

```shell
sudo ncat --sh-exec "ncat localhost 8443" -l 443 -k
```

Create `/etc/hosts` entries for any TLS-enabled AWS services to be mocked, for example:

```shell
sudo bash -c "echo 127.0.0.1 sts.amazonaws.com >> /etc/hosts"
```

Then when running the application, override its truststore, for example by setting java properties:

```properties
javax.net.ssl.trustStore=$(git rev-parse --show-toplevel)/swatch-producer-aws/mock-aws/test-ca.jks
javax.net.ssl.trustStorePassword=password
```
