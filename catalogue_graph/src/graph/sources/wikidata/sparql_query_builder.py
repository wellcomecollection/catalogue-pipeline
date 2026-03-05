from typing import Literal

from utils.types import NodeType, OntologyType

WikidataEdgeQueryType = Literal[
    "same_as_loc",
    "same_as_mesh",
    "instance_of",
    "subclass_of",
    "has_field_of_work",
    "has_father",
    "has_mother",
    "has_sibling",
    "has_spouse",
    "has_child",
    "has_founder",
    "has_industry",
]


class SparqlQueryBuilder:
    """
    Contains various methods for constructing reusable SPARQL queries to run against Wikidata's SPARQL endpoint.
    """

    @staticmethod
    def _compact_format_query(query: str) -> str:
        """
        Remove all line breaks and extra spaces from the query.
        """
        return " ".join(query.split())

    @staticmethod
    def _get_formatted_fields(node_type: NodeType) -> str:
        """
        Return the names of all fields to be retrieved via a SPARQL query based on node type.
        """
        fields = ["?item", "?itemLabel", "?itemDescription", "?itemAltLabel"]

        if node_type == "names":
            fields += ["?dateOfBirth", "?dateOfDeath", "?placeOfBirthLabel"]
        elif node_type == "locations":
            fields += ["?coordinates"]

        # The Wikidata id (stored under '?item') is the only field in the 'GROUP BY' clause. SPARQL requires that all
        # other fields are wrapped in an aggregate function. We use the 'SAMPLE' function, which chooses a value at
        # random where multiple values are available for some field. (Currently only the '?coordinates' field sometimes
        # stores multiple values, and we don't mind only extracting one of those values.)
        fields_with_aggregation = []
        for field in fields:
            if field == "?item":
                fields_with_aggregation.append(field)
            else:
                fields_with_aggregation.append(f"(SAMPLE({field}) as {field})")

        return " ".join(fields_with_aggregation)

    @staticmethod
    def _get_formatted_field_mappings(node_type: NodeType) -> str:
        """
        Returns SPARQL field definitions, mapping field names specified
        in the `_get_formatted_fields` method to Wikidata property codes.
        """
        definitions = []

        if node_type == "names":
            definitions += [
                "OPTIONAL { ?item wdt:P569 ?dateOfBirth. }",
                "OPTIONAL { ?item wdt:P570 ?dateOfDeath. }",
                "OPTIONAL { ?item wdt:P19 ?placeOfBirth. }",
            ]
        elif node_type == "locations":
            definitions += ["OPTIONAL { ?item p:P625/ps:P625 ?coordinates. }"]

        return "\n".join(definitions)

    @staticmethod
    def _get_label_mappings(node_type: NodeType) -> str:
        """
        Returns SPARQL label mappings using the `wikibase:label` service.
        """
        extra_mappings = []
        if node_type == "names":
            extra_mappings.append("?placeOfBirth rdfs:label ?placeOfBirthLabel.")

        label_mappings = f"""
        OPTIONAL {{
            SERVICE wikibase:label {{
                bd:serviceParam wikibase:language "en".
                ?item rdfs:label ?itemLabel.
                ?item schema:description ?itemDescription.
                ?item skos:altLabel ?itemAltLabel.
                {"\n".join(extra_mappings)}
            }}
        }}
        """

        return label_mappings

    @staticmethod
    def get_all_ids_query(linked_ontology: OntologyType) -> str:
        """
        Return a query to retrieve the ids of _all_ Wikidata items referencing an id from the `linked_ontology`.
        """
        if linked_ontology == "loc":
            field_filter = "?item wdt:P244 _:anyValueP244."
        elif linked_ontology == "mesh":
            field_filter = "?item wdt:P486 _:anyValueP486."
        else:
            raise ValueError(f"Invalid linked ontology type: {linked_ontology}")

        get_ids_query = f"""
            SELECT ?item WHERE {{
                {field_filter}
            }}
        """

        return SparqlQueryBuilder._compact_format_query(get_ids_query)

    @classmethod
    def get_items_query(cls, item_ids: list[str], node_type: NodeType) -> str:
        """
        Given a list of Wikidata `item_ids`, return a query to retrieve all required Wikidata fields for each id
        in the list.
        """
        ids_clause = " ".join([f"wd:{wikidata_id}" for wikidata_id in sorted(item_ids)])

        query = f"""
            SELECT DISTINCT {cls._get_formatted_fields(node_type)}
            WHERE {{
                VALUES ?item {{ {ids_clause} }}
                {cls._get_formatted_field_mappings(node_type)}
                {cls._get_label_mappings(node_type)}
            }}
            GROUP BY ?item
        """

        return SparqlQueryBuilder._compact_format_query(query)

    @classmethod
    def get_edge_query(
        cls, item_ids: list[str], edge_type: WikidataEdgeQueryType
    ) -> str:
        """
        Given a list of Wikidata `item_ids`, return a query to retrieve all edges of type `edge_type` linking each
        item in the list to a different Wikidata item.
        """
        ids_clause = " ".join([f"wd:{wikidata_id}" for wikidata_id in sorted(item_ids)])

        if edge_type == "same_as_loc":
            property_path = "p:P244/ps:P244"
        elif edge_type == "same_as_mesh":
            property_path = "p:P486/ps:P486"
        elif edge_type == "instance_of":
            property_path = "wdt:P31"
        elif edge_type == "subclass_of":
            property_path = "wdt:P279"
        elif edge_type == "has_field_of_work":
            property_path = "wdt:P101"
        elif edge_type == "has_founder":
            property_path = "wdt:P112"
        elif edge_type == "has_industry":
            property_path = "wdt:P452"
        elif edge_type == "has_father":
            property_path = "wdt:P22"
        elif edge_type == "has_mother":
            property_path = "wdt:P25"
        elif edge_type == "has_sibling":
            property_path = "wdt:P3373"
        elif edge_type == "has_spouse":
            property_path = "wdt:P26"
        elif edge_type == "has_child":
            property_path = "wdt:P40"
        else:
            raise ValueError(f"Unknown edge type: {edge_type}")

        query = f"""
            SELECT DISTINCT ?fromItem ?toItem
            WHERE {{
              VALUES ?fromItem {{ {ids_clause} }}
              ?fromItem {property_path} ?toItem.
              FILTER (!wikibase:isSomeValue(?toItem))
            }}
        """

        return SparqlQueryBuilder._compact_format_query(query)
