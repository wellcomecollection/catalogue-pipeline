import concurrent.futures
from collections.abc import Callable, Generator, Iterable
from itertools import batched, islice
from typing import TypeVar

T = TypeVar("T")
S = TypeVar("S")


def process_stream_in_parallel(
    stream: Iterable[T],
    process: Callable[[list[T]], list[S]],
    chunk_size: int,
    thread_count: int,
) -> Generator[S]:
    """
    Process items from a stream in parallel using multiple threads. Return a single generator streaming
    the processed items.

    Items are consumed from `stream` in chunks of size `chunk_size`. The `process` function is applied to each chunk in
    a separate thread. The number of parallel threads is kept under `thread_count`.
    """
    chunks = batched(stream, chunk_size, strict=False)

    with concurrent.futures.ThreadPoolExecutor() as executor:
        # Run the first `thread_count` threads in parallel
        futures = {
            executor.submit(process, list(chunk))
            for chunk in islice(chunks, thread_count)
        }

        while futures:
            # Wait for one or more threads to complete
            done, futures = concurrent.futures.wait(
                futures, return_when=concurrent.futures.FIRST_COMPLETED
            )

            # Top up with new queries to keep the total number of parallel threads at `thread_count`
            for chunk in islice(chunks, len(done)):
                futures.add(executor.submit(process, list(chunk)))

            for future in done:
                items = future.result()
                yield from items
