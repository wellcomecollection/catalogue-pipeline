ARG PYTHON_IMAGE_VERSION=latest 
FROM python:${PYTHON_IMAGE_VERSION} AS base

LABEL maintainer="Wellcome Collection <digital@wellcomecollection.org>"

ADD .python-version /app/.python_version
ADD src /app/src
ADD scripts /app/scripts

WORKDIR /app

RUN scripts/ci-setup.sh

ENTRYPOINT [ "/app/src/extractor.py" ]