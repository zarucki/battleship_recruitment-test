#!/bin/bash
URL="http://${3:-localhost}:${2:-8080}/game/${1:-gameId}/join"
echo "$URL" 1>&2
curl -s -XPOST "$URL"
