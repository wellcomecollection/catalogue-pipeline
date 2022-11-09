#!/usr/bin/env bash
<<EOF
Run tests for a Python Lambda in this repo.

== Usage ==

Pass the path to the folder containing the Lambda code (and in particular
the tox.ini for running tests), e.g.

    $ run_lambda_tests.sh calm_adapter/calm_window_generator

EOF

set -o errexit
set -o nounset
