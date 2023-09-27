FROM public.ecr.aws/docker/library/python:3.8-slim

RUN apt-get update && apt-get -y install gcc g++ libffi-dev curl

COPY requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# As the VGG model is a large external dependency, we build
# it into the image rather than fetching it at runtime.
COPY fetch_vgg.py /
RUN python ./fetch_vgg.py

COPY gunicorn.conf.py start.sh prestart.py /
COPY app /app

ENV PYTHONPATH=/app

EXPOSE 80
CMD ["./start.sh"]

