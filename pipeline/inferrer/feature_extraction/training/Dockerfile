FROM python:3.7

COPY requirements.txt requirements.txt
RUN pip install -r requirements.txt

COPY ./src /src
COPY ./train_lsh.py /train_lsh.py

CMD [ "python", "train_lsh.py" ]