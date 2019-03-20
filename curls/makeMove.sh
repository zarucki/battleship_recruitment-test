#!/bin/bash

URL="http://${5:-localhost}:${4:-8080}/game/${1:-gameId}"
AUTH_TOKEN="$2"
BODY='{
  "position": "'"$3"'"
}'

echo "PUT $URL" 1>&2
echo "$BODY" 1>&2
echo "$AUTH_TOKEN" 1>&2

curl -s -XPUT "$URL" -H "Content-Type: application/json" \
        --header "Set-Auth-Token: $AUTH_TOKEN" \
        --data "$BODY"
