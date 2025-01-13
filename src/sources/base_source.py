from collections.abc import Generator
import xml.etree.ElementTree as ET


class BaseSource:
    def stream_raw(self) -> Generator[dict | ET.Element]:
        """Returns a generator of dictionaries, each corresponding to a raw entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")
