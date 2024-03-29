FROM python:3.10

LABEL maintainer = "Wellcome Collection <digital@wellcomecollection.org>"

COPY ./src /app

WORKDIR /app

RUN pip install --no-cache-dir -r /app/test_requirements.txt
