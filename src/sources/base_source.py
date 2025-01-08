from collections.abc import Generator


class BaseSource:
    def stream_raw(self) -> Generator[dict]:
        """Returns a generator of dictionaries, each corresponding to a raw entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")
