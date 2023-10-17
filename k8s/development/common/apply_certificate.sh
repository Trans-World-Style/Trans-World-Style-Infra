#!/usr/bin/env bash

set -euo pipefail

THIS_SCRIPT_PATH=$(cd "$(dirname "$0")" && pwd)
cd "$THIS_SCRIPT_PATH"

NAMESPACE="dev"

# create the argocd namespace
kubectl create ns "$NAMESPACE" || echo "namespace '$NAMESPACE' already exists"

# apply certificate
kubectl apply -f ./certificate.yaml

sleep 1

# add annotation for kubed
kubectl annotate secret dev-tw-style-duckdns-tls-secret -n "$NAMESPACE" kubed.appscode.com/sync="app=dev"