import json

from test_mocks import MockRequest
from test_utils import load_fixture

from transformers.loc.concepts_transformer import LibraryOfCongressConceptsTransformer
from transformers.loc.locations_transformer import LibraryOfCongressLocationsTransformer


def jsons_to_ndjson(json_fixtures:list[str]) -> bytes:
    # Given a bunch of formatted JSON files, concatenate them into ndjson
    return "\n".join(
        json.dumps(json.loads(load_fixture(fixture))) for fixture in json_fixtures
    ).encode("utf-8")


def test_loc_concept_transformer_resilience() -> None:
    test_url = "https://example.com"

    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": test_url,
                "status_code": 200,
                "json_data": None,
                "content_bytes": "\n[}{}[\n".encode(
                    "utf-8"
                )  # This line fails, but should not break the overall process
                + jsons_to_ndjson(
                    [
                        "mads_geographic_concept.json",  # geographic concepts are not included in the concepts transformer output
                        "mads_composite_concept.json",
                        "mads_deprecated_concept.json",  # This one is deprecated, so is not included in the output
                        "mads_narrower_authority_concept.json",
                    ]
                ),
                "params": None,
            }
        ]
    )
    concepts_transformer = LibraryOfCongressConceptsTransformer(test_url)

    nodes = list(concepts_transformer.stream(entity_type="nodes", query_chunk_size=1))
    # mads_composite_concept and mads_narrower_authority_concept
    assert len(list(nodes)) == 2


def test_loc_location_transformer_resilience() -> None:
    test_url_subjects = "https://example.com/subjects"
    test_url_names = "https://example.com/names"

    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": test_url_subjects,
                "status_code": 200,
                "json_data": None,
                "content_bytes": "\n[}{}[\n".encode(
                    "utf-8"
                )  # This line fails, but should not break the overall process
                + jsons_to_ndjson(
                    [
                        "mads_geographic_concept.json",  # Only geographic concepts included in the location transformer output
                        "mads_composite_concept.json",
                        "mads_deprecated_concept.json",
                        "mads_narrower_authority_concept.json",
                    ]
                ),
                "params": None,
            },
            {
                "method": "GET",
                "url": test_url_names,
                "status_code": 200,
                "json_data": None,
                "content_bytes": "\n[}{}[\n".encode(
                    "utf-8"
                )  # This line fails, but should not break the overall process
                + load_fixture("loc_names_example.jsonld"),
                "params": None,
            },
        ]
    )
    locations_transformer = LibraryOfCongressLocationsTransformer(
        test_url_subjects, test_url_names
    )
    nodes = list(locations_transformer.stream(entity_type="nodes", query_chunk_size=1))
    # Caversham Park from mads_geographic_concept
    # and Budapest (Hungary) from loc_names_example
    assert len(list(nodes)) == 2


def test_empty_source()->None:
    """If there is nothing to process, nothing is emitted"""
    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": "/dev/null",
                "status_code": 200,
                "json_data": None,
                "content_bytes": b"",
                "params": None,
            }
        ]
    )
    transformer = LibraryOfCongressConceptsTransformer("/dev/null")
    assert list(transformer.stream(entity_type="nodes", query_chunk_size=1)) == []
