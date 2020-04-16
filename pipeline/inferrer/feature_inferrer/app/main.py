from fastapi import FastAPI, HTTPException

import src.http as http
from src.batching import BatchExecutionQueue
from src.feature_extraction import extract_features
from src.image import get_image_from_url, get_image_url_from_iiif_url
from src.logging import get_logstash_logger
from src.lsh import LSHEncoder

logger = get_logstash_logger(__name__)

# Initialise encoder
logger.info("Initialising LSHEncoder model")
lsh_encoder = LSHEncoder()

# initialise API
logger.info("Starting API")
app = FastAPI(
    title="Feature vector encoder",
    description="Takes an image url and returns the image's feature vector encoded as an LSH string",
)
logger.info("API started, awaiting requests")


def batch_infer_features(images):
    vectors = extract_features(images)
    lsh_encoded = lsh_encoder(vectors)
    return [{"vector": v, "lsh": l} for v, l in zip(vectors, lsh_encoded)]


batch_inferrer_queue = BatchExecutionQueue(batch_infer_features, batch_size=16, timeout=0.250)


@app.get("/feature-vector/")
async def main(image_url: str = None, iiif_url: str = None):
    if (not (image_url or iiif_url)) or (iiif_url and image_url):
        logger.error(f"client passed image_url: {image_url} iiif_url: {iiif_url}")
        raise HTTPException(
            status_code=400, detail="API takes one of: image_url, iiif_url"
        )

    if iiif_url:
        try:
            image_url = get_image_url_from_iiif_url(iiif_url)
        except ValueError as e:
            error_string = str(e)
            logger.error(error_string)
            raise HTTPException(status_code=400, detail=error_string)

    try:
        image = await get_image_from_url(image_url)
    except ValueError as e:
        error_string = str(e)
        logger.error(error_string)
        raise HTTPException(status_code=404, detail=error_string)

    features = await batch_inferrer_queue.execute(image)
    logger.info(f"extracted features from url: {image_url}")

    return {"features": features["vector"], "lsh_encoded_features": features["lsh"]}


@app.get("/healthcheck")
def healthcheck():
    return {"status": "healthy"}


@app.on_event("startup")
def on_startup():
    http.start_persistent_client_session()
    batch_inferrer_queue.start_worker()


@app.on_event("shutdown")
def on_shutdown():
    http.close_persistent_client_session()
    batch_inferrer_queue.stop_worker()
