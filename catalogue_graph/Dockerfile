ARG PYTHON_IMAGE_VERSION=latest 
FROM python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

# Set working directory
WORKDIR /app

# Copy dependency files
COPY pyproject.toml uv.lock ./

# Install uv package manager
RUN pip install uv

# Install dependencies and the package using uv pip install
# uv pip install works with the system Python environment and installs from uv.lock
# --system installs to system Python instead of requiring a virtual environment  
RUN uv pip install --system .

# Copy application source code
COPY src/ ./src/

# Make the extractor script executable
RUN chmod +x src/extractor.py

# Set Python path to include src directory
ENV PYTHONPATH="/app/src"

FROM base AS catalogue_graph_extractor

ENTRYPOINT [ "python", "/app/src/extractor.py" ]
