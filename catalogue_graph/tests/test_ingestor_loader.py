import polars as pl
import pytest
from test_mocks import MockRequest, MockSmartOpen

from ingestor_indexer import IngestorIndexerLambdaEvent
from ingestor_loader import IngestorLoaderConfig, IngestorLoaderLambdaEvent, handler
from models.catalogue_concept import CatalogueConcept, CatalogueConceptIdentifier


def build_test_matrix() -> list[tuple]:
    return [
        (
            "happy path, with alternative labels",
            IngestorLoaderLambdaEvent(
                job_id="123",
                start_offset=0,
                end_index=1,
            ),
            IngestorLoaderConfig(
                loader_s3_bucket="test-bucket",
                loader_s3_prefix="test-prefix",
            ),
            {
                "results": [
                    {
                        "source": {
                            "~properties": {
                                "id": "source_id",
                                "label": "label",
                                "type": "type",
                            }
                        },
                        "relationships": [],
                        "targets": [
                            {
                                "~properties": {
                                    "id": "456",
                                    "source": "source",
                                    "alternative_labels": "alternative_label||another_alternative_label",
                                }
                            }
                        ],
                    }
                ]
            },
            IngestorIndexerLambdaEvent(
                s3_uri="s3://test-bucket/test-prefix/123/00000000-00000001.parquet",
            ),
            CatalogueConcept(
                id="source_id",
                label="label",
                type="type",
                alternativeLabels=["alternative_label", "another_alternative_label"],
                identifiers=[
                    CatalogueConceptIdentifier(
                        value="456",
                        identifierType="source",
                    )
                ],
            ),
        ),
        (
            "happy path, with NO alternative labels",
            IngestorLoaderLambdaEvent(
                job_id="123",
                start_offset=0,
                end_index=1,
            ),
            IngestorLoaderConfig(
                loader_s3_bucket="test-bucket",
                loader_s3_prefix="test-prefix",
            ),
            {
                "results": [
                    {
                        "source": {
                            "~properties": {
                                "id": "source_id",
                                "label": "label",
                                "type": "type",
                            }
                        },
                        "relationships": [],
                        "targets": [
                            {
                                "~properties": {
                                    "id": "456",
                                    "source": "source",
                                }
                            }
                        ],
                    }
                ]
            },
            IngestorIndexerLambdaEvent(
                s3_uri="s3://test-bucket/test-prefix/123/00000000-00000001.parquet",
            ),
            CatalogueConcept(
                id="source_id",
                label="label",
                type="type",
                alternativeLabels=[],
                identifiers=[
                    CatalogueConceptIdentifier(
                        value="456",
                        identifierType="source",
                    )
                ],
            ),
        ),
        (
            "badly formed response",
            IngestorLoaderLambdaEvent(
                job_id="123",
                start_offset=0,
                end_index=1,
            ),
            IngestorLoaderConfig(
                loader_s3_bucket="test-bucket",
                loader_s3_prefix="test-prefix",
            ),
            {
                "results": [
                    {
                        "foo": "bar",
                    }
                ]
            },
            None,
            None,
        ),
    ]


def get_test_id(argvalue: str) -> str:
    return argvalue


@pytest.mark.parametrize(
    "description,event,config,neptune_response,expected_output,expected_concept",
    build_test_matrix(),
    ids=get_test_id,
)
def test_ingestor_trigger(
    description: str,
    event: IngestorLoaderLambdaEvent,
    config: IngestorLoaderConfig,
    neptune_response: dict,
    expected_output: IngestorIndexerLambdaEvent,
    expected_concept: CatalogueConcept,
) -> None:
    if expected_output is not None:
        MockSmartOpen.mock_s3_file(expected_output.s3_uri, "")

    MockRequest.mock_responses(
        [
            {
                "method": "POST",
                "url": "https://test-host.com:8182/openCypher",
                "status_code": 200,
                "json_data": neptune_response,
                "content_bytes": None,
                "params": None,
            }
        ]
    )

    if expected_output is not None:
        result = handler(event, config)

        assert result == expected_output
        assert len(MockRequest.calls) == 1

        request = MockRequest.calls[0]

        assert request["method"] == "POST"
        assert request["url"] == "https://test-host.com:8182/openCypher"

        with MockSmartOpen.open(expected_output.s3_uri, "r") as f:
            df = pl.read_parquet(f)
            assert len(df) == 1

            catalogue_concepts = [
                CatalogueConcept.model_validate(row) for row in df.to_dicts()
            ]

            assert len(catalogue_concepts) == 1
            assert catalogue_concepts[0] == expected_concept
    else:
        # This line is reachable, but mypy doesn't know that
        with pytest.raises(LookupError):  # type: ignore[unreachable]
            handler(event, config)
