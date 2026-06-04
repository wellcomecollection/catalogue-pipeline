import random
import string

# Seeded RNG for deterministic output — shared across all generators
rng = random.Random(0)


def reset() -> None:
    rng.seed(0)


def random_alphanumeric(length: int | None = None) -> str:
    length = length or rng.randint(5, 10)
    chars = string.ascii_letters + string.digits
    return "".join(rng.choice(chars) for _ in range(length))


def random_canonical_id() -> str:
    alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "".join(rng.choice(alphabet) for _ in range(8))
