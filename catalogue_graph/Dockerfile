ARG PYTHON_IMAGE_VERSION=latest 
FROM python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

# Set working directory
WORKDIR /app

# Copy dependency files
COPY pyproject.toml uv.lock ./

# Install uv and dependencies
RUN pip install uv && \
    uv sync --no-dev && \
    uv export --no-dev --format requirements-txt > requirements.txt && \
    pip install --no-deps -r requirements.txt

# Copy application source code
COPY src/ ./src/

# Make the extractor script executable
RUN chmod +x src/extractor.py

# Set Python path to include src directory
ENV PYTHONPATH="/app/src"

FROM base AS catalogue_graph_extractor

ENTRYPOINT [ "python", "/app/src/extractor.py" ]
