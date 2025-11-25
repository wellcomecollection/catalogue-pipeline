#!/bin/bash

set -euo pipefail
# Input format: "keyPrefix:version:xmlFilename"

METS_SPECS=${1:-"digitised/b16675630:1:b16675630 digitised/b10326285:2:b10326285 born-digital/PPCRI/N/16:1:METS.4e07d0d4-1658-45e5-b28d-1ab78baf44d7 born-digital/SAWFO/J/8:2:METS.28581356-8fe7-48dd-be47-9968551825cb"}

echo "📨 Sending test METS specs: $METS_SPECS"

echo "📤 Sending test messages to INPUT queue..."

for spec in $METS_SPECS; do
    # Split by colon to get keyPrefix, version, and xmlFilename
    # First extract keyPrefix (everything before first colon)
    keyPrefix="${spec%%:*}"
    # Remove keyPrefix and first colon to get "version:xmlFilename"
    rest="${spec#*:}"
    # Extract version (everything before the colon in rest)
    version="${rest%%:*}"
    # Extract xmlFilename (everything after the colon in rest)
    xmlFilename="${rest#*:}"
    
    # Extract mets_id from keyPrefix (last part after /)
    mets_id="${keyPrefix##*/}"
    
    echo "Sending message for keyPrefix: $keyPrefix, METS ID: $mets_id, XML filename: $xmlFilename (version: $version)"
    
    # Construct JSON message body
    # Full S3 path will be: {keyPrefix}/v{version}/data/{xmlFilename}.xml
    json_message="{\"Message\": \"{\\\"id\\\": \\\"$mets_id\\\", \\\"sourceData\\\": {\\\"type\\\": \\\"MetsFileWithImages\\\", \\\"root\\\": {\\\"bucket\\\": \\\"wellcomecollection-storage-staging\\\", \\\"keyPrefix\\\": \\\"$keyPrefix\\\"}, \\\"filename\\\": \\\"v$version/data/$xmlFilename.xml\\\", \\\"manifestations\\\": [], \\\"createdDate\\\": \\\"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\\\", \\\"version\\\": $version}, \\\"version\\\": $version}\"}"
    echo "Constructed JSON message: $json_message"
    
    # Send message to INPUT SQS queue - JSON formatted message body
    aws --endpoint-url=http://localhost:4566 sqs send-message \
        --queue-url http://localhost:4566/000000000000/mets-transformer-queue \
        --message-body "$json_message"
done

echo "✅ Test messages sent to input queue!"
echo "📊 Transformer will:"
echo "   - Read from: mets-transformer-queue (input)"
echo "   - Index results in: Elasticsearch works-source-dev index"