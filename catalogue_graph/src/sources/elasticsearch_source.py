from collections.abc import Generator
from queue import Queue
from threading import Thread
from typing import Tuple

from elasticsearch import Elasticsearch

from .base_source import BaseSource

ES_BATCH_SIZE = 1000
SLICE_COUNT = 10


class ElasticsearchSource(BaseSource):
    def __init__(self, url: str, index_name: str, basic_auth: Tuple[str, str]):
        self.es_client = Elasticsearch(url, basic_auth=basic_auth, timeout=600)
        self.index_name = index_name

    def search_with_pit(self, pit_id: int, slice_index: int, queue: Queue) -> None:
        # For now, only extract 'Visible' works.
        body = {
            "query": {"match_all": {}},
            #"query": {"match": {"type": "Visible"}},
            "size": ES_BATCH_SIZE,
            "pit": {"id": pit_id, "keep_alive": "5m"},
            "sort": [{"_shard_doc": "asc"}],
            "slice": {"id": slice_index, "max": SLICE_COUNT},
        }

        while True:
            hits = self.es_client.search(body=body)["hits"]["hits"]
            if not hits:
                break

            for hit in hits:
                queue.put(hit["_source"])

            body["search_after"] = hits[-1]["sort"]

        queue.put(None)    

    def stream_raw(self) -> Generator[dict]:
        pit = self.es_client.open_point_in_time(index=self.index_name, keep_alive="5m")

        q: Queue = Queue(maxsize=ES_BATCH_SIZE)
        threads = []
        for i in range(SLICE_COUNT):
            t = Thread(target=self.search_with_pit, args=(pit["id"], i, q), daemon=True)
            t.start()
            threads.append(t)
    
        done_signals = 0
        while done_signals < SLICE_COUNT:
            item = q.get()
            if item is None:
                done_signals += 1
            else:
                yield item        

        self.es_client.close_point_in_time(body={"id": pit["id"]})
