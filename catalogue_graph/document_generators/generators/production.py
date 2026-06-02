from models.pipeline.production import ProductionEvent

from .random import random_alphanumeric, rng
from .concepts import create_concept, create_period_for_year


def create_production_event() -> ProductionEvent:
    return ProductionEvent(
        label=random_alphanumeric(25),
        places=[create_concept()],
        agents=[create_concept()],
        dates=[create_period_for_year(str(rng.randint(1800, 2020)))],
    )