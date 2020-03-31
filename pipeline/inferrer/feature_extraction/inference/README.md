# Infer feature vectors

Use trained LSH models to infer feature vector strings for images

```
docker build -t feature_extraction_api .
docker run -v ~/.aws:/root/.aws -p 80:80 feature_extraction_api
```

```
curl "http://0.0.0.0/feature-vector/?image_url=SOME_ENCODED_IMAGE_URL"
```

## environment variables

- `LOGSTASH_HOST` is the IP address for the logstash instance
- `LOGSTASH_PORT` is the port for the logstash instance
- `AWS_PROFILE_NAME` is the AWS profile used to download trained model artefacts (optional, shouldn't be necessary in prod environment)
- `MODEL_OBJECT_KEY` is the s3 key for the pickled `LSHEncoder()` model
