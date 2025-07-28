from collections.abc import Generator
from typing import Any


class BaseSource:
    def stream_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to an entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")

    def stop_processing(self) -> None:
        """Signal the source to stop processing and terminate early."""
        print(
            f"Warning: stop_processing() called on {self.__class__.__name__}, but no implementation is provided."
        )
