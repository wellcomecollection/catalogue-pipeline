# Inferrer services (local Docker build)

Each inferrer service is a named build stage in the top-level Dockerfile in this directory.

## Building a single service

From this directory (change platform as needed):

```bash
docker build \
  -f Dockerfile \
  -t palette_inferrer \
  --target palette_inferrer \
  --platform linux/amd64 \
  --build-arg pythonversion="$(cat .python-version)" \
  .
```

Replace `palette_inferrer` with one of:

- `palette_inferrer`
- `feature_inferrer`
- `aspect_ratio_inferrer`

## Running a service locally

After building an image, you can run it like this:

```bash
docker run --platform linux/amd64 --rm -p 80:80 palette_inferrer
```

Healthcheck:

```bash
curl http://0.0.0.0:80/healthcheck
```

## Using the service (curl)

All the inferrers take an image URL as the `query_url` query parameter.

Palette inferrer:

```bash
curl "http://0.0.0.0:80/palette/?query_url=SOME_URL_ENCODED_IMAGE_URL"
```

Feature inferrer:

```bash
curl "http://0.0.0.0:80/feature-vector/?query_url=SOME_URL_ENCODED_IMAGE_URL"
```

Aspect ratio inferrer:

```bash
curl "http://0.0.0.0:80/aspect-ratio/?query_url=SOME_URL_ENCODED_IMAGE_URL"
```

## Running unit tests

Each service has its own unit tests in a `test/` directory.

Running the tests from this directory:

```bash
cd palette_inferrer && uv run pytest
```
