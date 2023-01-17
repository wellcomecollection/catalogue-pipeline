import numpy as np
import base64

from fastapi import FastAPI, HTTPException
from weco_datascience import http
from weco_datascience.batching import BatchExecutionQueue
from weco_datascience.image import get_image_from_url
from weco_datascience.logging import get_logger

from src.feature_extraction import extract_features

logger = get_logger(__name__)


# initialise API
logger.info("Starting API")
app = FastAPI(
    title="Feature vector encoder",
    description=(
        "Takes an image url and returns the image's feature vector, "
        "and a reduced 1024-dimensional form of the vector"
    ),
)
logger.info("API started, awaiting requests")


def feature_reducer(vectors: np.ndarray) -> np.ndarray:
    """
    return the first 1024 elements of a set of vectors, normalised
    to unit length. Normalisation is done to ensure that the vectors
    can be compared using dot_product similarity, rather than cosine.

    N.B. This is a placeholder for a more sophisticated dimensionality
    reduction technique
    """
    sliced = vectors[:, :1024]
    normalised_vectors = sliced / np.linalg.norm(sliced, axis=1, keepdims=True)
    return normalised_vectors


def batch_infer_features(images):
    vectors = extract_features(images)
    reduced = feature_reducer(vectors)
    return [{"vector": v, "reduced_vector": r} for v, r in zip(vectors, reduced)]


batch_inferrer_queue = BatchExecutionQueue(
    batch_infer_features, batch_size=16, timeout=0.250
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

    return {
        "features_b64": base64.b64encode(features["vector"]),
        "reduced_features_b64": base64.b64encode(features["reduced_vector"]),
    }


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
