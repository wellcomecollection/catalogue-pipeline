#!/usr/bin/env python3

from types import SimpleNamespace
import boto3
import requests

def one_plus(n):
    return n + 1

def lambda_handler(event, context):
    print("Lambda function ARN:", context.invoked_function_arn)
    print("Current identity: " + boto3.client('sts').get_caller_identity().get('Arn'))

    r = requests.get('https://api.wellcomecollection.org/catalogue/v2/works/jr9vvkum?include=items,partOf,identifiers')
    print(r.text)

    return {
        "message": "hi"
    }

if __name__ == "__main__":
    event = []
    context = SimpleNamespace(invoked_function_arn= None)
    lambda_handler(event, context)
