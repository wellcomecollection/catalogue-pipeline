from models.catalogue_concept import CatalogueConcept, CatalogueConceptIdentifier


def test_catalogue_concept_from_neptune_result() -> None:
    neptune_result = {
        "concept": {"~properties": {"id": "id", "label": "label", "type": "type"}},
        "source_concepts": [
            {
                "~properties": {
                    "id": "id",
                    "source": "lc-subjects",
                    "alternative_labels": "alternativeLabels||moreAlternativeLabels",
                    "description": "description",
                    "label": "Priority label",
                }
            },
            {
                "~properties": {
                    "id": "id",
                    "source": "wikidata",
                    "alternative_labels": "invisibleAlternativeLabel",
                    "description": "Non-priority description",
                }
            },
        ],
        "linked_source_concepts": [
            {
                "~properties": {
                    "id": "id",
                    "source": "lc-subjects",
                    "alternative_labels": "alternativeLabels||moreAlternativeLabels",
                    "description": "description",
                    "label": "Priority label",
                }
            }
        ],
    }

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[
            CatalogueConceptIdentifier(value="id", identifierType="lc-subjects")
        ],
        label="Priority label",
        alternativeLabels=["alternativeLabels", "moreAlternativeLabels"],
        description="description",
        type="type",
    )


def test_catalogue_concept_from_neptune_result_without_alternative_labels() -> None:
    neptune_result = {
        "concept": {
            "~properties": {
                "id": "id",
                "label": "label",
                "type": "type",
            }
        },
        "source_concepts": [
            {
                "~properties": {
                    "id": "id",
                    "source": "nlm-mesh",
                    "description": "description",
                }
            }
        ],
        "linked_source_concepts": [
            {
                "~properties": {
                    "id": "id",
                    "source": "nlm-mesh",
                    "description": "description",
                }
            }
        ],
    }

    assert CatalogueConcept.from_neptune_result(neptune_result) == CatalogueConcept(
        id="id",
        identifiers=[CatalogueConceptIdentifier(value="id", identifierType="nlm-mesh")],
        label="label",
        alternativeLabels=[],
        description="description",
        type="type",
    )
