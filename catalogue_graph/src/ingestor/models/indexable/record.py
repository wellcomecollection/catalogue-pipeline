from abc import ABC, abstractmethod
from datetime import datetime

from models.pipeline.serialisable import ElasticsearchModel


class IndexableRecord(ElasticsearchModel, ABC):
    @abstractmethod
    def get_id(self) -> str:
        raise NotImplementedError

    @abstractmethod
    def get_modified_time(self) -> datetime:
        raise NotImplementedError

    @staticmethod
    def from_raw_document(document: dict) -> "IndexableRecord":
        raise NotImplementedError
