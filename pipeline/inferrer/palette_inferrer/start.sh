#!/usr/bin/env bash
set -e

exec gunicorn -c gunicorn.conf.py --reload app.main:app
