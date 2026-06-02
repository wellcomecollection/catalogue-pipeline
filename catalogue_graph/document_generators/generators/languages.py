
from models.pipeline.id_label import Language

from .random import random_alphanumeric


def create_language() -> Language:
    return Language(
        id=random_alphanumeric(3),
        label=random_alphanumeric(),
    )
