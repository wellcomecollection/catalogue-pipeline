FROM python:3.8

LABEL maintainer = "Wellcome Collection <digital@wellcomecollection.org>"

RUN apt-get update && apt-get install -y zip
RUN mkdir /target

COPY ./src /app

WORKDIR /app

RUN pip install --no-cache-dir --target ./package -r /app/requirements.txt
RUN cd package
RUN zip -r ../lambda.zip .
RUN cd ..
RUN zip lambda.zip *.py


