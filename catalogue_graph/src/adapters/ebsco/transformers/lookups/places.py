import json
import os
from functools import cache

HERE = os.path.dirname(os.path.abspath(__file__))


@cache
def _load_places() -> dict[str, str]:
    with open(os.path.join(HERE, "places.json")) as json_file:
        return json.load(json_file)


def from_code(place_code: str) -> str | None:
    return _load_places().get(place_code)
