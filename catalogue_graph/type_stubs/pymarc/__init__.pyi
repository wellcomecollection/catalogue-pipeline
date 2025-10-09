from typing import IO

from pymarc.record import Record

def parse_xml_to_array(handle: IO[str]) -> list[Record]: ...
