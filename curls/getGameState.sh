#!/bin/bash
URL="http://${3:-localhost}:${2:-8080}/game/${1:-gameId}"
echo "$URL" 1>&2
curl -s -XGET "$URL"
