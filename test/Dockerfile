FROM debian:latest

RUN apt-get update && \
    apt-get install -y libssl-dev openssl

COPY test.sh .

CMD ["sh", "test.sh"]