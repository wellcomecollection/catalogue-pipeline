import typing


def lambda_handler(event: dict, context: typing.Any) -> None:
    print("Hello from the default lambda handler!")
