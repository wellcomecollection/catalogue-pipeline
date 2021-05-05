from fastapi import FastAPI, HTTPException
from weco_datascience import http
from weco_datascience.batching import BatchExecutionQueue
from weco_datascience.image import get_image_from_url
from weco_datascience.logging import get_logger
from .encoder import Encoder

logger = get_logger(__name__)


# initialise API
logger.info("Starting API")
app = FastAPI(title="Aspect ratio extractor", description="extracts aspect ratios")
logger.info("API started, awaiting requests")

encoder = Encoder()
batch_inferrer_queue = BatchExecutionQueue(encoder, batch_size=8, timeout=1)


@app.get("/aspect-ratio/")
async def main(query_url: str):
    try:
        image = await get_image_from_url(query_url)
    except ValueError as e:
        error_string = str(e)
        logger.error(error_string)
        raise HTTPException(status_code=404, detail=error_string)

    aspect_ratio = await batch_inferrer_queue.execute(image)
    logger.info(f"extracted aspect ratio from url: {query_url}")

    return {"aspect_ratio": aspect_ratio}


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
