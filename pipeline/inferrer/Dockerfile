FROM public.ecr.aws/docker/library/python:3.10-slim as base

RUN apt-get update && apt-get -y install gcc g++ libffi-dev curl

COPY common/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY common /

ENV PYTHONPATH=/app

EXPOSE 80
CMD ["./start.sh"]

FROM base as aspect_ratio_inferrer

COPY aspect_ratio_inferrer/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY aspect_ratio_inferrer/app app

FROM base as feature_inferrer

COPY feature_inferrer/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY feature_inferrer/fetch_vgg.py /
RUN python ./fetch_vgg.py
COPY feature_inferrer/app app

FROM base as palette_inferrer

COPY palette_inferrer/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY palette_inferrer/app app


