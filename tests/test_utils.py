import os


def load_fixture(file_name: str) -> bytes:
    with open(f"{os.path.dirname(__file__)}/fixtures/{file_name}", "rb") as f:
        return f.read()
