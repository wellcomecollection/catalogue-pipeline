FROM python:3.7-slim

RUN apt-get update && apt-get -y install gcc g++ libffi-dev

COPY requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY ./app ./gunicorn.conf.py ./start.sh /

ENV PYTHONPATH=/app

EXPOSE 80
CMD ["./start.sh"]

