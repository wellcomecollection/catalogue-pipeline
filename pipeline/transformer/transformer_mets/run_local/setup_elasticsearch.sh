#!/bin/bash

set -euo pipefail

echo "⏳ Waiting for Elasticsearch to be ready..."
until curl -s http://localhost:9200/_cluster/health | grep -q '"status":"green\|yellow"'; do
    echo "Waiting for Elasticsearch..."
    sleep 5
done

echo "📊 Creating works-source-dev index with custom mapping..."

curl -X PUT "localhost:9200/works-source-dev" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "analysis": {
      "normalizer": {
        "lowercase_normalizer": {
          "type": "custom",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "dynamic": "false",
    "properties": {
      "data": {
        "properties": {
          "otherIdentifiers": {
            "properties": {
              "value": {
                "type": "keyword",
                "normalizer": "lowercase_normalizer"
              }
            }
          }
        }
      },
      "state": {
        "properties": {
          "sourceIdentifier": {
            "dynamic": "false",
            "properties": {
              "value": {
                "type": "keyword",
                "normalizer": "lowercase_normalizer"
              },
              "identifierType.id": {
                "type": "keyword",
                "normalizer": "lowercase_normalizer"
              }
            }
          }
        }
      }
    }
  }
}
'

echo "✅ Elasticsearch index created successfully!"
echo "🌐 Kibana will be available at: http://localhost:5601"
echo "🔍 Elasticsearch API available at: http://localhost:9200"