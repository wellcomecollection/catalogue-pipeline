from collections.abc import Generator
from itertools import islice


def generator_to_chunks(items: Generator, chunk_size: int) -> Generator:
    while True:
        chunk = list(islice(items, chunk_size))
        if chunk:
            yield chunk
        else:
            return
