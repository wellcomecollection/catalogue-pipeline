from core.source import BaseSource


class BaseTransformer:
    def __init__(self) -> None:
        self.source: BaseSource = BaseSource()