import os

port = os.getenv("PORT", "80")
bind = f"0.0.0.0:{port}"
workers = 1
worker_class = "uvicorn.workers.UvicornWorker"
keepalive = 120
timeout = 120
