import base64

from fastapi import FastAPI, HTTPException
from common import http
from common.image import get_image_from_url
from common.logging import get_logger
from common.batching import BatchExecutionQueue

from src.feature_extraction import extract_features

logger = get_logger(__name__)

# initialise API
logger.info("Starting API")
app = FastAPI(
    title="Feature vector encoder",
    description=("Takes an image url and returns the image's feature vector"),
)
logger.info("API started, awaiting requests")


batch_inferrer_queue = BatchExecutionQueue(
    extract_features, batch_size=16, timeout=0.250
)


@app.get("/feature-vector/")
async def main(query_url: str):
    try:
        image = await get_image_from_url(query_url)
    except ValueError as e:
        error_string = str(e)
        logger.error(error_string)
        raise HTTPException(status_code=404, detail=error_string)

    features = await batch_inferrer_queue.execute(image)
    logger.info(f"extracted features from url: {query_url}")

    return {"features_b64": base64.b64encode(features)}


@app.get("/healthcheck")
def healthcheck():
    return {"status": "healthy"}


@app.on_event("startup")
def on_startup():
    http.start_persistent_client_session()
    batch_inferrer_queue.start_worker()


@app.on_event("shutdown")
async def on_shutdown():
    await http.close_persistent_client_session()
    batch_inferrer_queue.stop_worker()
