from abc import ABC, abstractmethod

from models.pipeline.serialisable import ElasticsearchModel


class IndexableRecord(ElasticsearchModel, ABC):
    @abstractmethod
    def get_id(self) -> str:
        raise NotImplementedError

    @staticmethod
    def from_raw_document(document: dict) -> "IndexableRecord":
        raise NotImplementedError
