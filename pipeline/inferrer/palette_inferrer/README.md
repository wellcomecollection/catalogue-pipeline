# Infer feature vectors

**Not currently in prod**

Extract colour palettes from images

```
docker build -t palette_extraction_api .
docker run -p 80:80 palette_extraction_api
```

```
curl "http://0.0.0.0/palette/?image_url=SOME_ENCODED_IMAGE_URL"
```
