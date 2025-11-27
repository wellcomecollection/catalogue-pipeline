"""
Go through all JSON files in a given directory and raise an error if any of them contain Elasticsearch dot notation.

Usage: `python3 dot_notation_checker.py <SOME_DIRECTORY_PATH>` 
"""
import json
import typing 
import os
import sys


def find_dot_keys(obj: typing.Any, path: str = ""):
    if isinstance(obj, dict):
        for key, value in obj.items():
            full_path = f"{path}.{key}" if path else key
            if "." in key:
                raise ValueError(f"Dot notation detected in key '{full_path}'")
            find_dot_keys(value, full_path)
    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            find_dot_keys(item, f"{path}[{i}]")


def main():
    directory = sys.argv[1]
    processed_files = 0
    
    for filename in os.listdir(directory):
        if filename.endswith(".json"):
            path = os.path.join(directory, filename)
            try:
                with open(path, "r") as f:
                    data = json.load(f)
                find_dot_keys(data)
                processed_files += 1
            except ValueError as e:
                raise ValueError(f"{filename}: {e}")
    
    if processed_files == 0:
        raise ValueError("No Elasticsearch mapping files found in the specified directory.")


if __name__ == "__main__":
    main()
