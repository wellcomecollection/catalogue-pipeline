import logging

from fastapi import FastAPI, HTTPException

from src.feature_extraction import extract_features
from src.image import get_image_from_url, get_image_url_from_iiif_url
from src.logging import get_logstash_logger
from src.lsh import LSHEncoder

logger = get_logstash_logger(__name__)

# Initialise encoder
logger.info('Initialising LSHEncoder model')
lsh_encoder = LSHEncoder()

# initialise API
logger.info('Starting API')
app = FastAPI(
    title='Feature vector encoder',
    description='Takes an image url and returns the image\'s feature vector encoded as an LSH string'
)
logger.info('API started, awaiting requests')


@app.get('/feature-vector/')
def main(image_url: str = None, iiif_url: str = None):
    if (not (image_url or iiif_url)) or (iiif_url and image_url):
        logger.error(
            f'client passed image_url: {image_url} iiif_url: {iiif_url}'
        )
        raise HTTPException(
            status_code=400,
            detail='API takes one of: image_url, iiif_url'
        )

    if iiif_url:
        try:
            image_url = get_image_url_from_iiif_url(iiif_url)
        except ValueError as e:
            error_string = str(e)
            logger.error(error_string)
            raise HTTPException(status_code=400, detail=error_string)

    try:
        image = get_image_from_url(image_url)
    except ValueError as e:
        error_string = str(e)
        logger.error(error_string)
        raise HTTPException(status_code=404, detail=error_string)

    features = extract_features(image)
    lsh_encoded_features = lsh_encoder(features)

    logger.info(f'extracted features from url: {image_url}')

    return {
        'features': features.tolist(),
        'lsh_encoded_features': lsh_encoded_features
    }


@app.get('/healthcheck')
def healthcheck():
    return {'status': 'healthy'}
