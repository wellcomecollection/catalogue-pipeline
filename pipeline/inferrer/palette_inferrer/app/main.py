from fastapi import FastAPI, HTTPException
from common import http
from common.batching import BatchExecutionQueue
from common.image import get_image_from_url
from common.logging import get_logger
import base64

from palette_encoder import PaletteEncoder

logger = get_logger(__name__)

# Initialise encoder
logger.info("Initialising PaletteEncoder model")
palette_encoder = PaletteEncoder()

# initialise API
logger.info("Starting API")
app = FastAPI(
    title="Palette extractor", description="extracts color palette vectors from images"
)
logger.info("API started, awaiting requests")

batch_inferrer_queue = BatchExecutionQueue(palette_encoder, batch_size=8, timeout=1)


@app.get("/palette/")
async def main(query_url: str):
    try:
        image = await get_image_from_url(query_url, size=100)
    except ValueError as e:
        error_string = str(e)
        logger.error(error_string)
        raise HTTPException(status_code=404, detail=error_string)

    palette_result = await batch_inferrer_queue.execute(image)
    logger.info(f"extracted color palette from url: {query_url}")

    return {
        "palette_embedding": base64.b64encode(palette_result["palette_embedding"]),
        "average_color_hex": palette_result["average_color_hex"],
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
