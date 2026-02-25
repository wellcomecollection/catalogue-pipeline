ARG PYTHON_IMAGE_VERSION=3.12
FROM public.ecr.aws/lambda/python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

# Install AWS CLI for use in the Lambda extensions (e.g. fetching secrets)
RUN dnf install -y awscli && dnf clean all

# Copy extensions for Lambda (e.g. for secrets)
COPY infra/lambda_extensions/bash_secrets_extension.sh /opt/extensions/bash_secrets_extension.sh

FROM base AS python_lambda_with_extensions

# Set working directory
WORKDIR /app

# Copy dependency files
COPY pyproject.toml uv.lock ./

# Install uv
RUN pip install uv 

# Install ca-certificates and git
RUN dnf install -y ca-certificates git && dnf clean all

# Copy and install custom certificates
COPY certs/* /etc/pki/ca-trust/source/anchors/
RUN update-ca-trust extract

# Install pip-system-certs so Python uses the system CA store (including the Sectigo certs above).
# This is installed separately from the main package to avoid affecting local development.
RUN pip install pip-system-certs==5.3

# Install dependencies and the package using uv pip install
# uv pip install works with the system Python environment and installs from uv.lock
# --system installs to system Python instead of requiring a virtual environment  
RUN uv pip install --system .

# Copy application source code
COPY src/ ${LAMBDA_TASK_ROOT}

FROM python_lambda_with_extensions AS unified_pipeline_lambda

CMD [ "default.lambda_handler" ]
