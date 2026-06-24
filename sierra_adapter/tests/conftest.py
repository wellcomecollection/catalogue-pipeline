import os
import sys

# The modules under test live in a few directories that aren't packages, so put
# them on sys.path the same way build_missing_windows.py does at runtime.
_SIERRA_ADAPTER = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

for path in (
    _SIERRA_ADAPTER,
    os.path.join(_SIERRA_ADAPTER, "sierra_progress_reporter"),
    os.path.join(_SIERRA_ADAPTER, "..", "common", "window_generator", "src"),
):
    path = os.path.abspath(path)
    if path not in sys.path:
        sys.path.insert(0, path)
