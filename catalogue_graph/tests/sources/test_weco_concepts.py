from sources.weco_concepts.concepts_source import WeCoConceptsSource


def test_weco_concepts_source() -> None:
    weco_concepts_source = WeCoConceptsSource()
    stream_result = list(weco_concepts_source.stream_raw())

    # Do some simple checks on WECO source decoding based on known data
    assert len(stream_result) > 0
    first_record = stream_result[0]
    assert first_record["label"] == "Acquired Immunodeficiency Syndrome (AIDS)"
