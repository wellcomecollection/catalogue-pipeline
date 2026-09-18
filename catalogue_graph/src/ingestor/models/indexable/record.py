from abc import ABC, abstractmethod

from models.pipeline.serialisable import ElasticsearchModel


class IndexableRecord(ElasticsearchModel, ABC):
    @abstractmethod
    def get_id(self) -> str:
        raise NotImplementedError

    @abstractmethod
    def get_version(self) -> int:
        """The external Elasticsearch version this record is written with.

        Higher wins: a write is refused if the stored document is already at a higher
        version. See ingestor.models.indexable.version for how each record type orders.
        """
        raise NotImplementedError

    @staticmethod
    def from_raw_document(document: dict) -> "IndexableRecord":
        raise NotImplementedError
