import math

from .random import rng


def random_vector(d: int, max_r: float = 1.0) -> list[float]:
    rand = _normalize(_random_normal(d))
    r = max_r * math.pow(rng.random(), 1.0 / d)
    return _round_vector(_scalar_multiply(r, rand))


def random_unit_length_vector(d: int) -> list[float]:
    return _round_vector(_normalize(_random_normal(d)))


def _random_normal(d: int) -> list[float]:
    return [rng.gauss(0.0, 1.0) for _ in range(d)]


def _normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vec))
    return _scalar_multiply(1.0 / norm, vec)


def _scalar_multiply(a: float, vec: list[float]) -> list[float]:
    return [a * x for x in vec]


def _round_vector(vec: list[float]) -> list[float]:
    """Round to 12 significant digits to eliminate platform-dependent ULP differences."""
    return [round(x, 12) for x in vec]
