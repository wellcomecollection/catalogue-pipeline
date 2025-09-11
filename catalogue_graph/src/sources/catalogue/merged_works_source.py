from queue import Queue

from sources.threaded_es_source import ThreadedElasticsearchSource


class MergedWorksSource(ThreadedElasticsearchSource):

    def worker_target(self, slice_index: int, queue: Queue) -> None:
        search_after = None
        while hits := self.search(slice_index, search_after):
            for hit in hits:
                queue.put(hit.get("_source"))

            search_after = hits[-1]["sort"]

        queue.put(None)
