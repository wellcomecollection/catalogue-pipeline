from collections.abc import Generator

import config
from models.events import IncrementalWindow
from utils.elasticsearch import get_client, get_standard_index_name

from sources.base_source import BaseSource

ES_BATCH_SIZE = 1000


class MergedWorksSource(BaseSource):
    def __init__(
        self,
        pipeline_date: str,
        pit_id: str,
        slice_id: int,
        max_slices: int,
        query: dict | None = None,
        fields: list | None = None,
        window: IncrementalWindow | None = None,
        is_local: bool = False,
        
    ):
        self.es_client = get_client("graph_extractor", pipeline_date, is_local)
        self.index_name = get_standard_index_name(
            config.ES_DENORMALISED_INDEX_NAME, pipeline_date
        )
        self.query = self._build_query(query, window)
        self.fields = fields
        self.pit_id = pit_id
        self.slice_id = slice_id
        self.max_slices = max_slices
    
    def _build_query(self, query: dict | None, window: IncrementalWindow | None) -> dict:
        full_query = {"match_all": {}} if query is None else query
        if window is not None:
            range_filter = window.to_merged_works_filter()
            full_query = {"bool": {"must": [full_query, range_filter]}}
        
        return full_query

    def search_with_pit(self) -> None:
        body = {
            "query": self.query,
            "size": ES_BATCH_SIZE,
            "pit": {"id": self.pit_id, "keep_alive": "5m"},
            "sort": [{"_shard_doc": "asc"}],
        }  
        
        if self.max_slices > 1:
            body["slice"] = {"id": self.slice_index, "max": self.max_slices}

        if self.fields is not None:
            body["_source"] = self.fields

        while True:
            hits = self.es_client.search(body=body)["hits"]["hits"]
            if not hits:
                break

            yield from hits

            body["search_after"] = hits[-1]["sort"]
    
    def stream_raw(self) -> Generator[dict]:
        yield from self.search_with_pit()
