from typing import Literal

NodeType = Literal["concepts", "names", "locations"]
LinkedSource = Literal["mesh", "loc"]


class SparqlQueryBuilder:
    @staticmethod
    def _get_formatted_fields(node_type: NodeType, linked_source: LinkedSource):
        fields = ["?item", "?itemLabel", "?itemDescription", "?itemAltLabel"]

        if node_type == "names":
            fields += ["?dateOfBirthLabel", "?dateOfDeathLabel", "?placeOfBirthLabel"]
        elif node_type == "locations":
            fields += ["?coordinateLocation"]

        if linked_source == "loc":
            fields.append("?libraryOfCongressId")
        elif linked_source == "mesh":
            fields.append("?meshId")

        return " ".join(fields)

    @staticmethod
    def _get_formatted_field_definitions(
        node_type: NodeType, linked_source: Literal["mesh", "loc"]
    ):
        definitions = []

        if node_type == "names":
            definitions += [
                "OPTIONAL { ?item wdt:P569 ?dateOfBirth. }",
                "OPTIONAL { ?item wdt:P570 ?dateOfDeath. }",
                "OPTIONAL { ?item wdt:P19 ?placeOfBirth. }",
            ]
        elif node_type == "locations":
            definitions += ["OPTIONAL {{ ?item wdt:P625 ?coordinateLocation. }}"]

        if linked_source == "loc":
            definitions.append("?item wdt:P244 ?libraryOfCongressId.")
        elif linked_source == "mesh":
            definitions.append("?item wdt:P486 ?meshId.")

        return "\n".join(definitions)

    @classmethod
    def get_items_query(
        cls, item_ids: list[str], node_type: NodeType, linked_source: LinkedSource
    ):
        ids_clause = " ".join([f"wd:{wikidata_id}" for wikidata_id in item_ids])

        query = f"""
            SELECT DISTINCT {cls._get_formatted_fields(node_type, linked_source)}
            WHERE {{
              SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en". }}
              
              VALUES ?item {{ {ids_clause} }}
              
              {cls._get_formatted_field_definitions(node_type, linked_source)}
            }}
        """

        return query
