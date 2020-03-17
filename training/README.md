# Train LSH models for VGG embeddings

```
docker build -t feature_extraction_lsh_training .
docker run -v ~/.aws:/root/.aws -v /local/path/to/feature_vectors:/feature_vectors feature_extraction_lsh_training
```

