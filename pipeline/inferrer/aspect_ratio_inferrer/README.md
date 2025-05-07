# Infer aspect ratio

```
docker build -t aspect_ratio_api .
docker run -p 80:80 aspect_ratio_api
```

```
curl "http://0.0.0.0/aspect-ratio/?query_url=SOME_ENCODED_IMAGE_URL"
```

## TODO

Could avoid fetching the full image by using the info.json when given a IIIF URL.
