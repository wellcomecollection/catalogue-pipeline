from models.pipeline.holdings import Holdings

from .locations import create_physical_location
from .random import random_alphanumeric, rng


def create_holdings(count: int = 1) -> list[Holdings]:
    return [
        Holdings(
            note=rng.choice([None, f"Holdings note: {random_alphanumeric()}"]),
            enumeration=[random_alphanumeric() for _ in range(rng.randint(0, 3))],
            location=create_physical_location(),
        )
        for _ in range(count)
    ]
