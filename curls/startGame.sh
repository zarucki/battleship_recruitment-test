#!/bin/bash
URL="http://${2:-localhost}:${1:-8080}/game"
echo "$URL" 1>&2
curl -s -XPOST "$URL"
