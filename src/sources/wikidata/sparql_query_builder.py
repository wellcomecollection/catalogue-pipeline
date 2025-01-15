from typing import Literal

NodeType = Literal["concepts", "names", "locations"]
OntologyType = Literal["mesh", "loc"]

# https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service/query_optimization
#             "https://query.wikidata.org/bigdata/namespace/wdq/sparql?explain",


class SparqlQueryBuilder:
    """
    Contains various methods for constructing reusable SPARQL queries to run against Wikidata's SPARQL endpoint.
    """

    @staticmethod
    def _get_formatted_fields(node_type: NodeType) -> str:
        """
        Return the names of all fields to be retrieved via a SPARQL query based on node type.
        """
        fields = ["?item", "?itemLabel", "?itemDescription", "?itemAltLabel"]

        if node_type == "names":
            fields += ["?dateOfBirthLabel", "?dateOfDeathLabel", "?placeOfBirthLabel"]
        elif node_type == "locations":
            fields += ["?coordinates"]

        return " ".join(fields)

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
            definitions += [
                """
                {
                SELECT ?item (SAMPLE(?coordinates) AS ?coordinates) {
                  ?item p:P625/ps:P625 ?coordinates.
                }
                GROUP BY ?item
              }
            """
            ]

        return "\n".join(definitions)

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

        return get_ids_query

    @classmethod
    def get_items_query(cls, item_ids: list[str], node_type: NodeType):
        """
        Given a list of Wikidata `item_ids`, return a query to retrieve all required Wikidata fields for each id
        in the list.
        """
        ids_clause = " ".join([f"wd:{wikidata_id}" for wikidata_id in item_ids])

        query = f"""
            SELECT DISTINCT {cls._get_formatted_fields(node_type)}
            WHERE {{
              SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en". }}
              VALUES ?item {{ {ids_clause} }}
              {cls._get_formatted_field_mappings(node_type)}
            }}
        """

        return query

    @classmethod
    def get_linked_ids_query(cls, item_ids: list[str], linked_ontology: OntologyType):
        """
        Given a list of Wikidata `item_ids`, return a query to retrieve all linked ontology ids referenced by each
        item in the list.
        """
        if linked_ontology == "loc":
            field_filter = "?item p:P244/ps:P244 ?linkedId."
        elif linked_ontology == "mesh":
            field_filter = "?item p:P486/ps:P486 ?linkedId."
        else:
            raise ValueError(f"Invalid linked ontology type: {linked_ontology}")

        ids_clause = " ".join([f"wd:{wikidata_id}" for wikidata_id in item_ids])

        query = f"""
            SELECT DISTINCT ?item ?linkedId 
            WHERE {{
              VALUES ?item {{ {ids_clause} }}
              {field_filter}
            }}
        """

        return query
