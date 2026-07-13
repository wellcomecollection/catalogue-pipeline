from models.events import (
    BulkLoaderEvent,
    ExtractorEvent,
    FullGraphRemoverEvent,
    IncrementalGraphRemoverEvent,
    IncrementalWindow,
)
from models.neptune_bulk_loader import (
    BulkLoadErrors,
    BulkLoadFeed,
    BulkLoadStatusResponse,
)
from utils.reporting import BulkLoaderReport, IncrementalGraphRemoverReport


def test_construct_bulk_loader_file_path() -> None:
    prefix = "graph_bulk_loader"
    for model in [ExtractorEvent, BulkLoaderEvent]:
        event = model(
            graph_date="2026-01-01",
            pipeline_date="2025-01-01",
            transformer_type="loc_concepts",
            entity_type="nodes",
        )
        assert (
            event.get_file_path()
            == f"graph-{event.graph_date}/pipeline-{event.pipeline_date}/{prefix}/full/loc_concepts__nodes.csv"
        )

        event = model(
            graph_date="2026-01-01",
            pipeline_date="2025-05-01",
            transformer_type="catalogue_works",
            entity_type="nodes",
            window=IncrementalWindow.model_validate(
                {"start_time": "2021-12-01T12:00", "end_time": "2025-01-01T12:00"}
            ),
        )
        assert (
            event.get_file_path()
            == f"graph-{event.graph_date}/pipeline-{event.pipeline_date}/{prefix}/windows/20211201T1200-20250101T1200/catalogue_works__nodes.csv"
        )


def test_construct_bulk_loader_s3_uri() -> None:
    for model in [ExtractorEvent, BulkLoaderEvent]:
        event = model(
            graph_date="2026-01-01",
            pipeline_date="2025-01-01",
            transformer_type="loc_concepts",
            entity_type="nodes",
        )
        assert (
            event.get_s3_uri()
            == f"s3://wellcomecollection-catalogue-graph/graph-{event.graph_date}/pipeline-{event.pipeline_date}/graph_bulk_loader/full/loc_concepts__nodes.csv"
        )

        event = model(
            graph_date="2026-01-01",
            pipeline_date="2025-05-01",
            transformer_type="catalogue_concepts",
            entity_type="edges",
            window=IncrementalWindow.model_validate({"end_time": "2025-01-01T12:00"}),
        )
        assert (
            event.get_s3_uri()
            == f"s3://wellcomecollection-catalogue-graph/graph-{event.graph_date}/pipeline-{event.pipeline_date}/graph_bulk_loader/windows/20250101T1145-20250101T1200/catalogue_concepts__edges.csv"
        )


def test_construct_full_graph_remover_s3_uri() -> None:
    event = FullGraphRemoverEvent(
        pipeline_date="2025-01-01",
        graph_date="2025-01-01",
        transformer_type="loc_concepts",
        entity_type="edges",
    )

    assert (
        event.get_s3_uri("parquet", "deleted_ids")
        == "s3://wellcomecollection-catalogue-graph/graph-2025-01-01/pipeline-2025-01-01/graph_remover/full/deleted_ids/loc_concepts__edges.parquet"
    )


def test_construct_incremental_graph_remover_s3_uri() -> None:
    event = IncrementalGraphRemoverEvent(
        pipeline_date="2025-01-01",
        graph_date="2025-01-01",
        transformer_type="catalogue_work_identifiers",
        entity_type="edges",
        window=IncrementalWindow.model_validate({"end_time": "2022-01-01T12:00"}),
    )

    assert (
        event.get_s3_uri("parquet", "deleted_ids")
        == "s3://wellcomecollection-catalogue-graph/graph-2025-01-01/pipeline-2025-01-01/graph_remover_incremental/windows/20220101T1145-20220101T1200/deleted_ids/catalogue_work_identifiers__edges.parquet"
    )


def test_construct_bulk_loader_report_s3_uri() -> None:
    mock_status = BulkLoadStatusResponse(
        overall_status=BulkLoadFeed(
            full_uri="",
            run_number=123,
            retry_number=0,
            status="LOAD_COMPLETED",
            total_time_spent=134,
            start_time=4532,
            total_records=25,
            total_duplicates=24,
            parsing_errors=0,
            datatype_mismatch_errors=0,
            insert_errors=0,
        ),
        errors=BulkLoadErrors(start_index=0, end_index=0, load_id="123", error_logs=[]),
    )

    event = BulkLoaderReport(
        pipeline_date="2025-01-01",
        graph_date="2025-01-01",
        transformer_type="catalogue_work_identifiers",
        entity_type="edges",
        window=IncrementalWindow.model_validate({"end_time": "2022-01-01T12:00"}),
        status=mock_status,
    )

    assert (
        event.get_s3_uri("json")
        == "s3://wellcomecollection-catalogue-graph/graph-2025-01-01/pipeline-2025-01-01/graph_bulk_loader/windows/20220101T1145-20220101T1200/report.catalogue_work_identifiers__edges.json"
    )


def test_construct_graph_remover_report_s3_uri() -> None:
    event = IncrementalGraphRemoverReport(
        pipeline_date="2025-01-01",
        graph_date="2025-01-01",
        transformer_type="catalogue_concepts",
        entity_type="nodes",
        window=IncrementalWindow.model_validate({"end_time": "2022-01-01T12:00"}),
        deleted_count=235,
    )

    assert (
        event.get_s3_uri("json")
        == "s3://wellcomecollection-catalogue-graph/graph-2025-01-01/pipeline-2025-01-01/graph_remover_incremental/windows/20220101T1145-20220101T1200/report.catalogue_concepts__nodes.json"
    )
