#!/bin/bash -xe

# Adding 2 subject alt names so that connections to either 'localhost'
# or 'kafka' will pass hostname verification
cat /etc/pki/tls/openssl.cnf - <<CONF > san.cnf
[ my_extensions ]
subjectAltName=DNS:localhost,DNS:kafka
CONF

# Create a self-signed CA cert
openssl req -new -x509 -newkey rsa:4096 -nodes -days 3650 -subj '/CN=Test CA' -out test-ca.crt -keyout test-ca.key

openssl genrsa -out client.key 4096
openssl genrsa -out server.key 4096

# Generate signing requests for client and server and add the SAN extensions to the server request
openssl req -new -key server.key -out server.csr -subj "/CN=Kafka Server" -config san.cnf -reqexts my_extensions
openssl req -new -key client.key -out client.csr -subj "/CN=Kafka Client"

# Sign the certificates
openssl x509 -req -days 3650 -in server.csr -out server.crt -CA test-ca.crt -CAkey test-ca.key -CAcreateserial -extensions v3_req -extensions my_extensions -extfile san.cnf && rm server.csr
openssl x509 -req -days 3650 -in client.csr -out client.crt -CA test-ca.crt -CAkey test-ca.key -CAcreateserial -extensions v3_req -extfile /etc/pki/tls/openssl.cnf && rm client.csr

# Create PKCS12 and JKS keystores for each certificate.  These are not strictly necessary for our
# current configuration but sometimes they are handy to have around and creating them is annoying
# enough that I want to spare people having to do it on an ad hoc basis.
openssl pkcs12 -export -in client.crt -inkey client.key -name 'kafka-client' -certfile test-ca.crt -caname 'Test CA' -passout pass:password -out client.p12
keytool -importkeystore -srckeystore client.p12 -srcstoretype pkcs12 -srcstorepass password -destkeystore client.jks -deststorepass password

openssl pkcs12 -export -in server.crt -inkey server.key -name 'localhost' -certfile test-ca.crt -caname 'Test CA' -passout pass:password -out server.p12
keytool -importkeystore -srckeystore server.p12 -srcstoretype pkcs12 -srcstorepass password -destkeystore server.jks -deststorepass password

openssl pkcs12 -export -in test-ca.crt -nokeys -name 'test-ca' -caname 'Test CA' -passout pass:password -out test-ca.p12
keytool -importcert -file test-ca.crt -alias 'test-ca' -keystore test-ca.jks -storepass password -noprompt

rm san.cnf
