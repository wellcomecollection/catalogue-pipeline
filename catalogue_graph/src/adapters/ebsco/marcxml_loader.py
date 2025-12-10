import re
from typing import IO, cast

import pyarrow as pa
import smart_open
from lxml import etree

XMLPARSER = etree.XMLParser(remove_blank_text=True)


class MarcXmlFileLoader:
    def __init__(self, schema: pa.Schema, namespace: str) -> None:
        self.schema = schema
        self.namespace = namespace

    @staticmethod
    def load_xml(xmlfile: IO[bytes]) -> etree._Element:
        return etree.parse(xmlfile, parser=XMLPARSER).getroot()

    @staticmethod
    def nodes_to_records(collection: etree._Element) -> list[dict[str, str]]:
        return [
            MarcXmlFileLoader.node_to_record(record_node) for record_node in collection
        ]

    @staticmethod
    def node_to_record(node: etree._Element) -> dict[str, str]:
        record_id = MarcXmlFileLoader.extract_record_id(node)
        # serialize XML
        content = etree.tostring(node, encoding="unicode", pretty_print=False)
        return {"id": record_id, "content": content}

    @staticmethod
    def extract_record_id(node: etree._Element) -> str:
        def _strip_marc_parenthetical_prefix(raw_value: str) -> str:
            value = raw_value.strip()
            return re.sub(r"^\(.*\)", "", value)

        controlfield_values = cast(
            list[str],
            node.xpath("./*[local-name()='controlfield' and @tag='001']/text()"),
        )
        for value in controlfield_values:
            normalized = value.strip()
            if normalized:
                return normalized

        datafield_values = cast(
            list[str],
            node.xpath(
                "./*[local-name()='datafield' and @tag='035']/*[local-name()='subfield' and @code='a']/text()"
            ),
        )
        for raw_value in datafield_values:
            cleaned = _strip_marc_parenthetical_prefix(raw_value)
            if cleaned:
                return cleaned

        raise Exception(
            "Could not find controlfield 001 or usable datafield 035 "
            f"in record: {etree.tostring(node, encoding='unicode', pretty_print=True)}"
        )

    def data_to_pa_table(self, data: list[dict[str, str]]) -> pa.Table:
        namespaced = [{"namespace": self.namespace, **row} for row in data]
        return pa.Table.from_pylist(namespaced, schema=self.schema)

    def load_file(self, file_location: str) -> pa.Table:
        with smart_open.open(file_location, "rb") as xmlfile:
            collection = MarcXmlFileLoader.load_xml(xmlfile)
            records = MarcXmlFileLoader.nodes_to_records(collection)
            pa_table = self.data_to_pa_table(records)
            return pa_table
