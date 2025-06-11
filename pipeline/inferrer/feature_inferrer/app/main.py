import numpy as np
import base64
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from common import http
from common.image import get_image_from_url
from common.logging import get_logger
from common.batching import BatchExecutionQueue

from src.feature_extraction import extract_features

logger = get_logger(__name__)

# initialise API
logger.info("Starting API")


@asynccontextmanager
async def lifespan(app: FastAPI):
    startup()
    yield
    await shutdown()


app = FastAPI(
    title="Feature vector encoder",
    description=("Takes an image url and returns the image's feature vector"),
    lifespan=lifespan
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
    normalised_features = features / np.linalg.norm(features, axis=0, keepdims=True)

    logger.info(f"extracted features from url: {query_url}")

    return {"features_b64": base64.b64encode(normalised_features)}


@app.get("/healthcheck")
def healthcheck():
    return {"status": "healthy"}


def startup():
    http.start_persistent_client_session()
    batch_inferrer_queue.start_worker()


async def shutdown():
    await http.close_persistent_client_session()
    batch_inferrer_queue.stop_worker()
