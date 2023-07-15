FROM debian:latest

# Install Certbot
RUN apt-get update \
  && apt-get install -y certbot libssl-dev openssl\
  && rm -rf /var/lib/apt/lists/*

# Add renewal script
COPY renew-script.sh /etc/letsencrypt/renew-script.sh
RUN chmod +x /etc/letsencrypt/renew-script.sh

# Set working directory
WORKDIR /etc/letsencrypt

CMD ["sh", "renew-script.sh"]
