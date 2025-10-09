from adapters.ebsco.transformers.parsers.positional_field import PositionalField


class RawField006(PositionalField):
    """
    006 is a fixed width field, properties are extracted from
    specific character ranges within the field value
    https://www.loc.gov/marc/bibliographic/bd006.html
    """

    control_field_code = "006"

    @property
    def form_of_item(self) -> str:
        """
        Form of Item is character 6 in the 006 field
        >>> RawField006("m     o  d  |||||").form_of_item
        'o'
        """
        return self.field_value[6]
