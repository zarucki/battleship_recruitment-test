#!/bin/bash
URL="http://${4:-localhost}:${3:-8080}/game/${1:-gameId}"
AUTH_TOKEN="$2"

echo "GET $URL" 1>&2
echo "$AUTH_TOKEN" 1>&2

curl -s -XGET "$URL" --header "Set-Auth-Token: $AUTH_TOKEN"
