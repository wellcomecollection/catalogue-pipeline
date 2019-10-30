# API diff tool

This compares the results of calling both the production and staging APIs, showing a diff of what has changed between them for a particular API call.

It is intended to be used before switching stage to prod, and makes best sense when both are pointing to the same index.

## Installation

```
virtualenv venv
. venv/bin/activate
pip install -r requirements.txt
```

## Usage

Run by:

```
./diff_tool.py
```

Show all options with:

```
./diff_tool.py --help
```
