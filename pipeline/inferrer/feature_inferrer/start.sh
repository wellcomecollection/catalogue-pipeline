#!/usr/bin/env bash
set -e

python prestart.py
exec gunicorn -c gunicorn.conf.py --reload app.main:app
