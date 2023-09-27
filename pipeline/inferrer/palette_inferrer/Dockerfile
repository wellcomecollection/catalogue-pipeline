FROM public.ecr.aws/docker/library/python:3.9-slim

RUN apt-get update && apt-get -y install gcc g++ libffi-dev curl

COPY requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY gunicorn.conf.py start.sh /
COPY app /app

ENV PYTHONPATH=/app

EXPOSE 80
CMD ["./start.sh"]

