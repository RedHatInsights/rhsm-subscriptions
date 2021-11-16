1. Create a CA

   ```
   $ openssl req -new -x509 -newkey rsa:4096 -nodes -days 3650 -subj '/CN=Test CA' -out test-ca.crt -keyout test-ca.key
   ```

2. Create keys for the client and server

   ```
   $ openssl genrsa -out client.key 4096
   $ openssl genrsa -out server.key 4096
   ```

3. Create CSRs

   ```
   $ openssl req -new -key server.key -out server.csr -subj "/CN=localhost"
   $ openssl req -new -key client.key -out client.csr -subj "/CN=Client"
   ```

4. Sign the CSRs and remove them.  The `-extensions` and `-extfile` make
   the cert a v3 cert.  Otherwise, you get a v1

   ```
   $ openssl x509 -req -days 3650 -in server.csr -out server.crt -CA test-ca.crt -CAkey test-ca.key -CAcreateserial -extensions v3_req -extfile /etc/pki/tls/openssl.cnf && rm server.csr
   $ openssl x509 -req -days 3650 -in client.csr -out client.crt -CA test-ca.crt -CAkey test-ca.key -CAcreateserial -extensions v3_req -extfile /etc/pki/tls/openssl.cnf && rm client.csr
   ```

5. Create a PKCS 12 to hold the client's cert, key, and the CA that
   signed the cert.  Convert to JKS.  The PKCS12 is required as keytool
    does not all the import of a private key directly into a new JKS.

  ```
  $ openssl pkcs12 -export -in client.crt -inkey client.key -name 'Client' -certfile test-ca.crt -caname 'Test CA' -passout pass:password -out client.p12
  $ keytool -importkeystore -srckeystore client.p12 -srcstoretype pkcs12 -srcstorepass password -destkeystore client.jks -deststorepass password
  $ rm client.p12
  ```

6. Create a PKCS12 to hold the server's cert, key, and the CA that
   signed the cert.  Convert to JKS.

   ```
   $ openssl pkcs12 -export -in server.crt -inkey server.key -name 'localhost' -certfile test-ca.crt -caname 'Test CA' -passout pass:password -out server.p12
   $ keytool -importkeystore -srckeystore server.p12 -srcstoretype pkcs12 -srcstorepass password -destkeystore server.jks -deststorepass password
   $ rm server.p12
   ```

7. Create a JKS to hold the CA and act as a truststore.

   ```
   $ keytool -importcert -file test-ca.crt -alias 'Test CA' -keystore test-ca.jks -storepass password -noprompt
   ```

