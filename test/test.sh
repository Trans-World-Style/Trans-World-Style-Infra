#! /bin/bash
mkdir workspace2
cp -rL /workspace/live/tw-style.duckdns.org /workspace2
chmod -R 777 /workspace2/tw-style.duckdns.org
cd /workspace2/tw-style.duckdns.org
openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out keystore.p12 -name ttp -CAfile chain.pem -caname root -passin pass:0000 -passout pass:0000