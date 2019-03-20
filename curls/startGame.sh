#!/bin/bash
URL="http://${2:-localhost}:${1:-8080}/game"
echo "POST $URL" 1>&2
curl -sv -XPOST "$URL"
