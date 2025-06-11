from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from common import http
from common.image import get_image_from_url
from common.logging import get_logger

logger = get_logger(__name__)

# initialise API
logger.info("Starting API")


@asynccontextmanager
async def lifespan(app: FastAPI):
    http.start_persistent_client_session()
    yield
    await http.close_persistent_client_session()


app = FastAPI(
    title="Aspect ratio extractor",
    description="extracts aspect ratios",
    lifespan=lifespan,
)
logger.info("API started, awaiting requests")


@app.get("/aspect-ratio/")
async def main(query_url: str):
    """
    Determines the aspect ratio of a given image,
    where the aspect ratio (R) = width / height.
    By definition, R should always be positive.

    0 < R < 1 : portrait
    r = 1 : square
    R > 1 : landscape
    """
    try:
        image = await get_image_from_url(query_url)
    except ValueError as e:
        error_string = str(e)
        logger.error(error_string)
        raise HTTPException(status_code=404, detail=error_string)

    aspect_ratio = image.width / image.height
    logger.info(f"extracted aspect ratio from url: {query_url}")
    return {"aspect_ratio": aspect_ratio}


@app.get("/healthcheck")
def healthcheck():
    return {"status": "healthy"}


@app.on_event("startup")
def on_startup():
    http.start_persistent_client_session()


@app.on_event("shutdown")
async def on_shutdown():
    await http.close_persistent_client_session()
