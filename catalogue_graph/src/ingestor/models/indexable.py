from abc import ABC, abstractmethod

from pydantic import BaseModel


class IndexableRecord(BaseModel, ABC):
    @abstractmethod
    def get_id(self) -> str: raise NotImplementedError
