#!/usr/bin/env bash

set -ex

declare -a client_array=(client01.example.com localhost)

#create a temp directory for the CSRs
mkdir -p csr

# generate the key for the certificate authority
openssl genrsa -aes128 -out private_keys/ca.pem -passout pass:password 4096

# make a certificate signing request but output a certificate signed by the CA
# key instead of a csr, include the common name of 'ca'
openssl req -new -key private_keys/ca.pem -x509 -days 1000 -out certs/ca.pem -subj /CN=ca -passin pass:password

# copy the CA cert to the CA directory
cp certs/ca.pem ca/ca_crt.pem

# create a blank database file for the CA config
touch inventory

# create a CA emulator with a blank database and use that to generate a CRL
openssl ca -name ca -config <(echo database = inventory) -keyfile private_keys/ca.pem -passin pass:password -cert certs/ca.pem -md sha256 -gencrl -crldays 1000 -out ca/ca_crl.pem

# make CSRs and private keys for the clients
for client in "${client_array[@]}"
do
  openssl req -new -sha256 -nodes -out csr/${client}.csr -newkey rsa:4096 -keyout private_keys/${client}.pem -subj /CN=${client}
done

# generate the client certs from the CSRs
for client in "${client_array[@]}"
do
  openssl x509 -req -in csr/${client}.csr -CA certs/ca.pem -CAkey private_keys/ca.pem -CAcreateserial -out certs/${client}.pem -passin pass:password -days 999 -sha256 -extfile v3.ext
done

# clean up
rm -rf csr
rm certs/ca.srl inventory
