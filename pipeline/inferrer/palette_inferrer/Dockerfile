FROM python:3.7-slim

RUN apt-get update && apt-get -y install gcc g++ libffi-dev curl

COPY requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY gunicorn.conf.py start.sh /
COPY app /app

ENV PYTHONPATH=/app

EXPOSE 80
CMD ["./start.sh"]

