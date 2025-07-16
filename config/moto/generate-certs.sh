#!/bin/bash
openssl req -new -x509 -newkey rsa:4096 -nodes -days 3650 -subj '/CN=Test CA' -out test-ca.crt -keyout test-ca.key
openssl genrsa -out test-server.key 4096
openssl req -new -key test-server.key -out test-server.csr -subj "/CN=*.amazonaws.com"
openssl x509 -req -days 3650 -in test-server.csr -out test-server.crt -CA test-ca.crt -CAkey test-ca.key -CAcreateserial -extensions v3_req -extfile /etc/pki/tls/openssl.cnf
keytool -importcert -file test-ca.crt -alias 'Test CA' -keystore test-ca.jks -storepass password -noprompt

# NOTE you can cleanup via rm -f *.crt *.key *.p12 *.jks *.srl *.csr
