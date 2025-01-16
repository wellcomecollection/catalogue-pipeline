from collections.abc import Iterator, Generator
from itertools import islice
from typing import Callable, TypeVar, Any
import concurrent.futures

T = TypeVar("T")
S = TypeVar("S")


def generator_to_chunks(items: Iterator[Any], chunk_size: int) -> Generator[list]:
    """
    Split items in a generator into chunks of size `chunk_size` and return another generator yielding the chunks
    one by one.
    """
    while True:
        chunk = list(islice(items, chunk_size))
        if chunk:
            yield chunk
        else:
            return


def process_stream_in_parallel(
    stream: Iterator[T],
    process: Callable[[list[T]], list[S]],
    chunk_size: int,
    thread_count: int,
) -> Generator[S]:
    """
    Consume items from `stream` in chunks of size `chunk_size`. Apply the `process` function to each chunk in a new
    thread. Keep the number of parallel threads under `thread_count`. Return a single generator streaming the processed
    items.
    """
    chunks = generator_to_chunks(stream, chunk_size)

    with concurrent.futures.ThreadPoolExecutor() as executor:
        # Run the first `thread_count` threads in parallel
        futures = {
            executor.submit(process, chunk) for chunk in islice(chunks, thread_count)
        }

        while futures:
            # Wait for one or more threads to complete
            done, futures = concurrent.futures.wait(
                futures, return_when=concurrent.futures.FIRST_COMPLETED
            )

            # Top up with new queries to keep the total number of parallel threads at `thread_count`
            for chunk in islice(chunks, len(done)):
                futures.add(executor.submit(process, chunk))

            for future in done:
                items = future.result()
                for item in items:
                    yield item
