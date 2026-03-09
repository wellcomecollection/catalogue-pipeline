"""Lookup-only IdResolver backed by the RDS Data API.

Uses the AWS RDS Data API (``rds-data`` client) to query the identifiers
table without requiring direct network access to the RDS instance.
Intended for local / CLI usage where a pymysql connection isn't practical.
"""

from __future__ import annotations

from collections import defaultdict
from typing import Any

import boto3
import structlog

from id_minter.config import IdMinterConfig
from id_minter.models.identifier import SourceId

logger = structlog.get_logger(__name__)

_BATCH_SIZE = 200  # Data API has a 64 KB response limit; keep batches small


class DataApiIdResolver:
    """IdResolver that queries RDS via the Data API.

    Satisfies the ``IdResolver`` Protocol (lookup_ids + mint_ids).
    ``mint_ids`` deliberately raises — this is lookup-only.
    """

    def __init__(self, config: IdMinterConfig) -> None:
        session = boto3.Session()
        rds = session.client("rds", region_name=config.rds_region)
        cluster = rds.describe_db_clusters(DBClusterIdentifier=config.rds_cluster_id)[
            "DBClusters"
        ][0]
        self._cluster_arn = cluster["DBClusterArn"]
        self._secret_arn = cluster["MasterUserSecret"]["SecretArn"]
        self._database = config.db_name
        self._table_name = config.db_table
        self._rds_data = session.client("rds-data", region_name=config.rds_region)

    def _execute(
        self, sql: str, parameters: list[dict[str, Any]]
    ) -> list[dict[str, str]]:
        response = self._rds_data.execute_statement(
            resourceArn=self._cluster_arn,
            secretArn=self._secret_arn,
            database=self._database,
            sql=sql,
            parameters=parameters,
            includeResultMetadata=True,
        )
        columns = [col["name"] for col in response.get("columnMetadata", [])]
        rows: list[dict[str, str]] = []
        for record in response.get("records", []):
            row = {
                col_name: next(iter(field.values()))
                for col_name, field in zip(columns, record, strict=False)
            }
            rows.append(row)
        return rows

    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
        if not source_ids:
            return {}

        batches: dict[tuple[str, str], list[str]] = defaultdict(list)
        for ont, sys, val in source_ids:
            batches[(ont, sys)].append(val)

        result: dict[SourceId, str] = {}
        for (ontology_type, source_system), values in batches.items():
            for i in range(0, len(values), _BATCH_SIZE):
                chunk = values[i : i + _BATCH_SIZE]
                placeholders = ", ".join(f":sid{j}" for j in range(len(chunk)))
                params: list[dict[str, Any]] = [
                    {"name": f"sid{j}", "value": {"stringValue": v}}
                    for j, v in enumerate(chunk)
                ]
                params.extend(
                    [
                        {"name": "ont", "value": {"stringValue": ontology_type}},
                        {"name": "sys", "value": {"stringValue": source_system}},
                    ]
                )
                sql = (
                    f"SELECT OntologyType, SourceSystem, SourceId, CanonicalId "
                    f"FROM `{self._table_name}` "
                    f"WHERE OntologyType = :ont AND SourceSystem = :sys "
                    f"AND SourceId IN ({placeholders})"
                )
                for row in self._execute(sql, params):
                    key: SourceId = (
                        row["OntologyType"],
                        row["SourceSystem"],
                        row["SourceId"],
                    )
                    result[key] = row["CanonicalId"]

        logger.info(
            "Looked up identifiers via Data API",
            requested=len(source_ids),
            found=len(result),
        )
        return result

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]:
        source_ids = [src for src, _ in requests]
        result = self.lookup_ids(source_ids)
        missing = set(source_ids) - set(result)
        if missing:
            raise NotImplementedError(
                f"DataApiIdResolver cannot mint new IDs. "
                f"{len(missing)} identifier(s) not found in the database."
            )
        return result
