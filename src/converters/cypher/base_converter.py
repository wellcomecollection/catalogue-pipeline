class CypherBaseConverter:
    """
    Converts various data types into a format compatible with openCypher.
    """

    def _convert_str(self, raw_value: str) -> str:
        return raw_value

    def _convert_bool(self, raw_value: bool) -> str:
        return str(raw_value).lower()

    def _convert_none(self) -> str:
        return "null"

    def _convert_list(self, raw_value: list[any]) -> str:
        # Neptune does not support lists, so we convert them to a single string with a `||` separator
        return self._raw_value_to_cypher_value("||".join(raw_value))

    def _raw_value_to_cypher_value(self, raw_value: any) -> str:
        if isinstance(raw_value, str):
            value = self._convert_str(raw_value)
        elif isinstance(raw_value, bool):
            value = self._convert_bool(raw_value)
        elif isinstance(raw_value, list):
            value = self._convert_list(raw_value)
        elif raw_value is None:
            value = self._convert_none()
        else:
            raise TypeError(
                f"""
                    Cannot convert type {type(raw_value)} (with value {repr(raw_value)}) into an openCypher-compatible
                    data type. Use a different type or add support for type {type(raw_value)}.
                    """
            )

        return value
