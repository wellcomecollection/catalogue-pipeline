FROM python:3.10

WORKDIR /app

ADD src /app
RUN pip install -r requirements.txt

ENTRYPOINT ["python", "main.py"]
