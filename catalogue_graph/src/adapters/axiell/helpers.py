from adapters.axiell.config import (
    RECONCILER_LOCAL_CONFIG,
    RECONCILER_REST_API_CONFIG,
)
from adapters.utils.iceberg import (
    IcebergTable,
    get_local_table,
    get_rest_api_table,
)


def build_reconciler_table(
    *,
    use_rest_api_table: bool = True,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    if use_rest_api_table:
        return get_rest_api_table(RECONCILER_REST_API_CONFIG, create_if_not_exists)

    return get_local_table(RECONCILER_LOCAL_CONFIG, create_if_not_exists)
