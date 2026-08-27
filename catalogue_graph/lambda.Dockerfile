ARG PYTHON_IMAGE_VERSION=3.12
FROM public.ecr.aws/lambda/python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

# Copy extensions for Lambda (e.g. for secrets)
COPY infra/lambda_extensions/secrets_extension.py /opt/extensions/secrets_extension.py

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

# Install the locked dependencies into the system interpreter.
# --locked fails the build if uv.lock is out of date with pyproject.toml.
# --inexact keeps packages outside the lock (pip-system-certs above, the runtime's own).
# The project itself is not installed because src/ is copied in below.
RUN UV_PROJECT_ENVIRONMENT=/var/lang uv sync --locked --inexact --no-dev --no-install-project

# Copy application source code
COPY src/ ${LAMBDA_TASK_ROOT}

FROM python_lambda_with_extensions AS unified_pipeline_lambda

CMD [ "default.lambda_handler" ]
