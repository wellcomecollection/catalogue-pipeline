from models.catalogue_concept import CatalogueConcept, CatalogueConceptIdentifier


def test_catalogue_concept_from_neptune_result() -> None:
    neptune_result = {
        "source": {"~properties": {"id": "id", "label": "label", "type": "type", "description": "description"}},
        "targets": [
            {
                "~properties": {
                    "id": "id",
                    "source": "source",
                    "alternative_labels": "alternativeLabels||moreAlternativeLabels",
                }
            }
        ],
    }

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[CatalogueConceptIdentifier(value="id", identifierType="source")],
        label="label",
        alternativeLabels=["alternativeLabels", "moreAlternativeLabels"],
        description="description",
        type="type",
    )


def test_catalogue_concept_from_neptune_result_without_alternative_labels() -> None:
    neptune_result = {
        "source": {"~properties": {"id": "id", "label": "label", "type": "type", "description":"description"}},
        "targets": [
            {
                "~properties": {
                    "id": "id",
                    "source": "source",
                }
            }
        ],
    }

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[CatalogueConceptIdentifier(value="id", identifierType="source")],
        label="label",
        alternativeLabels=[],
        description="description",
        type="type",
    )
