from pydantic import BaseModel
from abc import ABC, abstractmethod


class IndexableRecord(BaseModel, ABC):
    @abstractmethod
    def get_id(self) -> str: raise NotImplementedError
