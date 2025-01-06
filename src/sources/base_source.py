from collections.abc import Generator


class BaseSource:
    def stream_raw(self) -> Generator[dict]:
        raise NotImplementedError("Each source must implement a `stream_raw` method.")
