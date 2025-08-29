from typing import IO

from models.marc import MarcRecord

def parse_xml_to_array(handle: IO[str]) -> list[MarcRecord]: ...
