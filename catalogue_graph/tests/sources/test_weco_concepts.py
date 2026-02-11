from sources.weco_concepts.concepts_source import WeCoConceptsSource


def test_weco_concepts_source() -> None:
    """
    The WeCo concepts source reads from a CSV file included in the repo.
    """
    weco_concepts_source = WeCoConceptsSource()
    stream_result = list(weco_concepts_source.stream_raw())
    # At time of writing there are 36 records in the CSV
    # we don't want to have to update this test every time a new record is added
    # so just check there are more than 35
    assert len(stream_result) > 35
    first_record = stream_result[0]
    # Because the CSV is actually in this repo, we can treat the real data as test data
    # If the data for AIDS changes significantly, we'll have to update this test
    assert first_record["label"] == "Acquired Immunodeficiency Syndrome (AIDS)"
    assert first_record["id"] == "zbus63qt"
    assert first_record["description"].startswith(
        "Thousands of images, texts and films"
    )
    assert "b16692342_l0052826.jp2" in first_record["image_url"]
