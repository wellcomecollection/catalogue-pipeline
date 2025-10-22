import utils.elasticsearch
from ingestor.models.step_events import IngestorStepEvent
from utils.elasticsearch import ElasticsearchMode, get_standard_index_name


class ElasticsearchRemover:
    def __init__(self, event: IngestorStepEvent, es_mode: ElasticsearchMode):
        api_key_name = f"{event.ingestor_type}_ingestor"
        index_name_prefix = f"{event.ingestor_type}-indexed"
        self.index_name = get_standard_index_name(index_name_prefix, event.index_date)

        self.client = utils.elasticsearch.get_client(
            api_key_name, event.pipeline_date, es_mode
        )

    def get_document_count(self) -> int:
        """Return the number of documents currently stored in ES in the given index."""
        response = self.client.count(index=self.index_name)
        count: int = response.get("count", 0)
        return count

    def delete_documents(self, deleted_ids: set[str]) -> int:
        """Remove documents matching `deleted_ids` from the given ES index."""
        query = {"query": {"ids": {"values": list(deleted_ids)}}}
        response = self.client.delete_by_query(index=self.index_name, body=query)

        deleted_count: int = response["deleted"]
        print(f"Deleted {deleted_count} documents from the {self.index_name} index.")
        return deleted_count
