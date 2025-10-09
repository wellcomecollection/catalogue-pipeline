"""Re-export shared mocks from unified `mocks` module.

This file exists to preserve the historical import path
`from tests.test_mocks import ...` while consolidating implementation
in a single place (`tests/mocks.py`). New tests should import from
`tests.mocks` directly.
"""

from .mocks import *  # noqa: F401,F403
