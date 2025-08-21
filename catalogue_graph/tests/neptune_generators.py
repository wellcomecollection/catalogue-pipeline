def a_related_concept() -> dict:
    return {
        "concept_node": {
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
        "edge": {
            "~id": "RELATED_TO:Q26255666-->Q26257473",
            "~entityType": "relationship",
            "~start": "Q26255666",
            "~end": "Q26257473",
            "~type": "RELATED_TO",
            "~properties": {"relationship_type": "has_sibling"},
        },
        "source_concept_nodes": [
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
        "concept_types": ["Person", "Concept"],
    }


def a_related_concept_with_no_label() -> dict:
    """
    This concept can be used to demonstrate how we handle bad records
    """
    return {
        "concept_node": {
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
        "edge": {
            "~id": "RELATED_TO:Q26255666-->Q26257473",
            "~entityType": "relationship",
            "~start": "Q26255666",
            "~end": "Q26257473",
            "~type": "RELATED_TO",
            "~properties": {"relationship_type": "has_sibling"},
        },
        "source_concept_nodes": [
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
        "concept_types": ["Person", "Concept"],
    }


def a_related_concept_with_two_source_nodes() -> dict:
    """
    This related concept can be used to demonstrate label precedence
    """
    return {
        "concept_node": {
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
        "edge": {
            "~id": "RELATED_TO:Q26255666-->Q26257473",
            "~entityType": "relationship",
            "~start": "Q26255666",
            "~end": "Q26257473",
            "~type": "RELATED_TO",
            "~properties": {"relationship_type": "has_sibling"},
        },
        "source_concept_nodes": [
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
        "concept_types": ["Person", "Concept"],
    }
