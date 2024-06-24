# this dockerfile should be used to build the ebsco_adapter image, it will use python 3.10 and install the dependencies from the requirements.txt file
# it will also copy the ebsco_adapter folder into the image and set the working directory to the ebsco_adapter folder
# it will then run the ebsco_adapter.py file
#

FROM python:3.10

WORKDIR /app


ADD src /app
RUN pip install -r requirements.txt

ENTRYPOINT ["python", "main.py"]
