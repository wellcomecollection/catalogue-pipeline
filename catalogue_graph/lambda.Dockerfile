ARG PYTHON_IMAGE_VERSION=latest 
FROM public.ecr.aws/lambda/python:${PYTHON_IMAGE_VERSION} AS base

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
COPY src/ ${LAMBDA_TASK_ROOT}
