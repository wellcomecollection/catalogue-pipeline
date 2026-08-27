ARG PYTHON_IMAGE_VERSION=3.12
FROM python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

# Set working directory
WORKDIR /app

# Copy dependency files
COPY pyproject.toml uv.lock ./

# Install uv and pip-system-certs
# pip-system-certs allows using system CA certificates when making HTTPS requests
RUN pip install uv==0.12.6

# Install git and clean up apt cache
RUN apt-get update && apt-get install -y ca-certificates git && rm -rf /var/lib/apt/lists/*

# Copy and install custom certificates
COPY certs/* /usr/local/share/ca-certificates/
RUN update-ca-certificates

# Install pip-system-certs so Python uses the system CA store (including the Sectigo certs above).
# This is installed separately from the main package to avoid affecting local development.
RUN pip install pip-system-certs==5.3

# Install the locked dependencies into the system interpreter.
# --locked fails the build if uv.lock is out of date with pyproject.toml.
# --inexact keeps packages outside the lock, such as pip-system-certs above.
# The project itself is not installed because src/ is copied in below.
RUN UV_PROJECT_ENVIRONMENT=/usr/local uv sync --locked --inexact --no-dev --no-install-project

# Copy application source code
COPY src/ ./src/

# Make the extractor script executable
RUN chmod +x src/graph/steps/extractor.py

# Set Python path to include src directory
ENV PYTHONPATH="/app/src"

FROM base AS unified_pipeline_task

ENTRYPOINT [ "python" ]
