#!/bin/sh

# The environment variables DOMAIN and EMAIL should be set.
if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
  echo "The DOMAIN and EMAIL environment variables must be set."
  exit 1
fi

# If the certificate is not present, request a new one.
# If the certificate is present but near expiry, request a new one.
# Otherwise, sleep.
while :; do
  if ! certbot certificates | grep -q "$DOMAIN"; then
    certbot certonly --standalone -d "$DOMAIN" -m "$EMAIL" --agree-tos --no-eff-email
    openssl pkcs12 -export -in ./live/$DOMAIN/fullchain.pem -inkey ./live/$DOMAIN/privkey.pem -out ./live/$DOMAIN/keystore.p12 -name ttp -CAfile ./live/$DOMAIN/chain.pem -caname root -passin pass:0000 -passout pass:0000
  elif openssl x509 -checkend 2592000 -noout -in "/etc/letsencrypt/live/$DOMAIN/fullchain.pem"; then
    echo "The certificate is up to date, sleeping..."
    sleep 12h
    continue
  else
    certbot renew
    openssl pkcs12 -export -in ./live/$DOMAIN/fullchain.pem -inkey ./live/$DOMAIN/privkey.pem -out ./live/$DOMAIN/keystore.p12 -name ttp -CAfile ./live/$DOMAIN/chain.pem -caname root -passin pass:0000 -passout pass:0000
  fi
  sleep 12h
done
