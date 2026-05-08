from adapters.extractors.ebsco.config import (
    LOCAL_ICEBERG_CONFIG,
    REST_API_ICEBERG_CONFIG,
)
from adapters.utils.iceberg import (
    IcebergTable,
    get_local_table,
    get_rest_api_table,
)


def build_adapter_table(
    use_rest_api_table: bool, create_if_not_exists: bool = True
) -> IcebergTable:
    if use_rest_api_table:
        return get_rest_api_table(REST_API_ICEBERG_CONFIG, create_if_not_exists)

    return get_local_table(LOCAL_ICEBERG_CONFIG, create_if_not_exists)
