#!/bin/bash

URL="http://${3:-localhost}:${2:-8080}/game/${1:-gameId}"
BODY='{
}'

echo "$URL" 1>&2
echo "$BODY" 1>&2

curl -s -XPUT "$URL" -H "Content-Type: application/json" \
	--data "$BODY"
