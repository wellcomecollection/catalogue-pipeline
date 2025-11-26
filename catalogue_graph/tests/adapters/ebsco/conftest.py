import os
from collections.abc import Generator

import pytest

# Add the test directory to the path so we can import from it
HERE = os.path.dirname(os.path.realpath(__file__))


@pytest.fixture
def xml_with_one_record() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_one_record.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_two_records() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_two_records.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def xml_with_three_records() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "with_three_records.xml")) as xmlfile:
        yield xmlfile


@pytest.fixture
def not_xml() -> Generator[object, None, None]:
    with open(os.path.join(HERE, "data", "not_xml.xml")) as xmlfile:
        yield xmlfile
