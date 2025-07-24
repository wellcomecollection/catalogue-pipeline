ARG PYTHON_IMAGE_VERSION=latest 
FROM python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

ADD .python-version /app/.python-version
ADD pyproject.toml /app/pyproject.toml
ADD uv.lock /app/uv.lock
ADD src /app/src

WORKDIR /app

RUN pip install uv && uv sync --no-dev

FROM base AS catalogue_graph_extractor

ENTRYPOINT [ "/app/src/extractor.py" ]
