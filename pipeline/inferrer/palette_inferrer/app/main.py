import base64
from fastapi import FastAPI, HTTPException

import src.http as http
from src.batching import BatchExecutionQueue
from src.image import get_image_from_url, get_image_url_from_iiif_url
from src.logging import get_logger
from src.palette_extractor import PaletteExtractor

logger = get_logger(__name__)

# Initialise encoder
logger.info("Initialising PaletteExtractor model")
palette_encoder = PaletteExtractor()

# initialise API
logger.info("Starting API")
app = FastAPI(title="Palette extractor", description="extracts palettes")
logger.info("API started, awaiting requests")


batch_inferrer_queue = BatchExecutionQueue(
    palette_encoder, batch_size=16, timeout=0.250
)


@app.get("/palette/")
async def main(image_url: str = None, iiif_url: str = None):
    if (not (image_url or iiif_url)) or (iiif_url and image_url):
        logger.error(
            f"client passed image_url: {image_url} iiif_url: {iiif_url}")
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

    response = {"palette": palette_encoder(image)}
    logger.info(f"extracted palette from url: {image_url}")
    return response


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
