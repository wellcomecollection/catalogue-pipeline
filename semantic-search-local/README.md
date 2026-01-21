# Semantic Search Local Experimentation

This directory contains a local Elasticsearch setup for experimenting with semantic search on Wellcome Collection catalogue data.

## Model

Using **msmarco-MiniLM-L12-cos-v5**:
- Optimized for search relevance (trained on MS MARCO dataset)
- 384 dimensions, fast inference
- Works on Apple Silicon (unlike ELSER)
- Good balance of speed and quality for catalogue search

## Setup

### 1. Start Elasticsearch

```bash
docker-compose up -d
```

Wait for Elasticsearch to be ready (check with `curl http://localhost:9200/_cluster/health`).

### 2. Install Python dependencies

```bash
uv sync
```

### 3. Open the Jupyter notebook

```bash
uv run jupyter notebook setup_semantic_search.ipynb
```

The notebook walks through:
1. Activating trial license (required for ML features)
2. Importing the model from Hugging Face using Eland
3. Deploying the model in Elasticsearch
4. Creating an inference endpoint
5. Setting up an index with semantic search fields
6. Testing with sample documents

```bash
curl -X PUT "localhost:9200/_inference/sparse_embedding/my-elser-model" \
  -H 'Content-Type: application/json' \
  -d '{
    "service": "elser",
    "service_settings": {
      "model_id": ".elser_model_2_linux-x86_64",

## Usage Examples

Once the model is deployed (via the notebook), you can query with semantic search:

```bash
curl -X POST "localhost:9200/works-semantic-local/_search" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "semantic": {
        "field": "titleSemantic",
        "query": "books about physics and the universe"
      }
    }
  }'
```

## Cleanup

```bash
docker-compose down -v  # Remove containers and volumes
```