from ingestor.models.neptune.query_result import NeptuneConcept, NeptuneRelatedConcept


def a_related_concept() -> NeptuneConcept:
    raw_concept = {
        "concept": {
            "~id": "tzrtx26u",
            "~entityType": "node",
            "~labels": ["Concept"],
            "~properties": {
                "id": "tzrtx26u",
                "label": "Hilton, Violet, 1908-1969.",
                "source": "lc-names",
                "type": "Person",
            },
        },
        "source_concepts": [
            {
                "~id": "n2006095131",
                "~entityType": "node",
                "~labels": ["SourceName"],
                "~properties": {
                    "id": "n2006095131",
                    "label": "Hilton, Violet, 1908-1969",
                    "source": "lc-names",
                },
            }
        ],
        "linked_source_concept": {
            "~id": "n2006095131",
            "~entityType": "node",
            "~labels": ["SourceName"],
            "~properties": {
                "id": "n2006095131",
                "label": "Hilton, Violet, 1908-1969",
                "source": "lc-names",
            },
        },
        "types": ["Person", "Concept"],
        "same_as": [],
    }

    return NeptuneRelatedConcept(target=raw_concept, relationship_type="has_sibling")


def a_related_concept_with_no_label() -> NeptuneConcept:
    """
    This concept can be used to demonstrate how we handle bad records
    """
    raw_concept = {
        "concept": {
            "~id": "aaaaaaaa",
            "~entityType": "node",
            "~labels": ["Concept"],
            "~properties": {
                "id": "aaaaaaaa",
                "label": None,
                "source": "lc-names",
                "type": "Person",
            },
        },
        "source_concepts": [
            {
                "~id": "n2006095131",
                "~entityType": "node",
                "~labels": ["SourceName"],
                "~properties": {
                    "id": "n2006095131",
                    "label": None,
                    "source": "lc-names",
                },
            }
        ],
        "linked_source_concept": {
            "~id": "n2006095131",
            "~entityType": "node",
            "~labels": ["SourceName"],
            "~properties": {
                "id": "n2006095131",
                "label": None,
                "source": "lc-names",
            },
        },
        "types": ["Person", "Concept"],
        "same_as": [],
    }

    return NeptuneRelatedConcept(target=raw_concept, relationship_type="has_sibling")


def a_related_concept_with_two_source_nodes() -> NeptuneConcept:
    """
    This related concept can be used to demonstrate label precedence
    """
    raw_concept = {
        "concept": {
            "~id": "abcd2345",
            "~entityType": "node",
            "~labels": ["Concept"],
            "~properties": {
                "id": "abcd2345",
                "label": "JP",
                "source": "lc-names",
                "type": "Person",
            },
        },
        "source_concepts": [
            {
                "~id": "n84165387",
                "~entityType": "node",
                "~labels": ["SourceName"],
                "~properties": {
                    "id": "n84165387",
                    "label": "Pujol, Joseph, 1857-1945.",
                    "source": "lc-names",
                },
            },
            {
                "~id": "Q318582",
                "~entityType": "node",
                "~labels": ["SourceName"],
                "~properties": {
                    "id": "Q318582",
                    "label": "Joseph Pujol",
                    "source": "wikidata",
                },
            },
        ],
        "linked_source_concept": {
            "~id": "n84165387",
            "~entityType": "node",
            "~labels": ["SourceName"],
            "~properties": {
                "id": "n84165387",
                "label": "Pujol, Joseph, 1857-1945.",
                "source": "lc-names",
            },
        },
        "types": ["Person", "Concept"],
        "same_as": [],
    }

    return NeptuneRelatedConcept(target=raw_concept, relationship_type="has_sibling")
