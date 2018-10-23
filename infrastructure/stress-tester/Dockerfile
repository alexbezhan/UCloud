FROM alpine:3.8

RUN apk add --no-cache python3
RUN apk add --no-cache alpine-sdk
RUN apk add --no-cache python3-dev
RUN apk add --no-cache libffi libffi-dev
RUN apk add --no-cache openssl openssl-dev
RUN pip3 install requests
RUN pip3 install jwt
COPY . /usr/src/app
