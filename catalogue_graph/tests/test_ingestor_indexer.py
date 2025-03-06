import pytest
import polars

from test_utils import load_fixture
from test_mocks import MockElasticsearchClient, MockSmartOpen
from ingestor_indexer import handler, IngestorIndexerLambdaEvent, IngestorIndexerConfig
   
def test_ingestor_indexer_success() -> None:
    config = IngestorIndexerConfig()
    event = IngestorIndexerLambdaEvent(s3_url="s3://test-catalogue-graph/00000000-00000004.parquet")
    MockSmartOpen.mock_s3_file(
        "s3://test-catalogue-graph/00000000-00000004.parquet",
        load_fixture("00000000-00000004.parquet")
    )
    MockSmartOpen.open(event.s3_url, "r")
    
    result = handler(event, config)

    expected_bulk_data = [{
      "_index": "concepts-indexed",
      "_id": "a2233f9d",
      "_source": {
        "query": {
          "id": "a2233f9d",
          "identifiers": [],
          "label": "Circle of State Librarians Conference 1979 : Kew, England)",
          "alternativeLabels": [],
          "type": "Meeting"
        },
        "display": {
          "id": "a2233f9d",
          "identifiers": [],
          "label": "Circle of State Librarians Conference 1979 : Kew, England)",
          "alternativeLabels": [],
          "type": "Meeting"
        }
      }
    },
    {
      "_index": "concepts-indexed",
      "_id": "a223f5a6",
      "_source": {
        "query": {
          "id": "a223f5a6",
          "identifiers": [
            {
              "value": "no2001062332",
              "identifierType": "lc-names"
            }
          ],
          "label": "Nelson, Geoffrey B. (Geoffrey Brian)",
          "alternativeLabels": [],
          "type": "Person"
        },
        "display": {
          "id": "a223f5a6",
          "identifiers": [
            {
              "id": "no2001062332",
              "label": "lc-names",
              "type": "IdentifierType"
            }
          ],
          "label": "Nelson, Geoffrey B. (Geoffrey Brian)",
          "alternativeLabels": [],
          "type": "Person"
        }
      }
    },
    {
      "_index": "concepts-indexed",
      "_id": "a2249bxm",
      "_source": {
        "query": {
          "id": "a2249bxm",
          "identifiers": [],
          "label": "Wolff, G.",
          "alternativeLabels": [],
          "type": "Person"
        },
        "display": {
          "id": "a2249bxm",
          "identifiers": [],
          "label": "Wolff, G.",
          "alternativeLabels": [],
          "type": "Person"
        }
      }
    },
    {
      "_index": "concepts-indexed",
      "_id": "a224b9mp",
      "_source": {
        "query": {
          "id": "a224b9mp",
          "identifiers": [
            {
              "value": "n79066466",
              "identifierType": "lc-names"
            }
          ],
          "label": "Jones, John E.",
          "alternativeLabels": [],
          "type": "Person"
        },
        "display": {
          "id": "a224b9mp",
          "identifiers": [
            {
              "id": "n79066466",
              "label": "lc-names",
              "type": "IdentifierType"
            }
          ],
          "label": "Jones, John E.",
          "alternativeLabels": [],
          "type": "Person"
        }
      }
    }]


    assert len(MockElasticsearchClient.inputs) == 4
    assert result == 4 # success count 
    assert MockElasticsearchClient.inputs == expected_bulk_data

    # MockElasticsearchClient.bulk.called_once_with(expected_bulk_data)

def build_test_matrix() -> list[tuple]:
    return [
        (
            "the file at s3_url doesn't exist",
            IngestorIndexerLambdaEvent(
                s3_url="s3://test-catalogue-graph/ghost-file"
            ),
            KeyError,
            'Mock S3 file s3://test-catalogue-graph/ghost-file does not exist.'
        ),
        (
            "the S3 file doesn't contain valid data",
            IngestorIndexerLambdaEvent(
                s3_url="s3://test-catalogue-graph/catalogue_example.json"
            ),
            polars.exceptions.ComputeError,
            'parquet: File out of specification: The file must end with PAR1'
        )
    ]

def get_test_id(argvalue: str) -> str:
    return argvalue

@pytest.mark.parametrize(
    "description,event,expected_error,error_message",
    build_test_matrix(),
    ids=get_test_id,
)

def test_ingestor_indexer_failure(
    description,
    event,
    expected_error,
    error_message
) -> None:
    config = IngestorIndexerConfig()
    

    with pytest.raises(expected_exception=expected_error, match=error_message):  # type: ignore[unreachable]
        if description != "the file at s3_url doesn't exist":
            MockSmartOpen.mock_s3_file(
              event.s3_url,
              load_fixture("catalogue_example.json")
            )
        MockSmartOpen.open(event.s3_url, "r")
        
        handler(event, config)


