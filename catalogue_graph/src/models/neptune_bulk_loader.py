from models.pipeline.serialisable import SerialisableModel


class BulkLoadFeed(SerialisableModel):
    full_uri: str
    run_number: int
    retry_number: int
    status: str
    total_time_spent: int
    start_time: int
    total_records: int
    total_duplicates: int
    parsing_errors: int
    datatype_mismatch_errors: int
    insert_errors: int


class BulkLoadErrorLog(SerialisableModel):
    error_code: str
    error_message: str
    file_name: str
    record_num: int


class BulkLoadErrors(SerialisableModel):
    start_index: int
    end_index: int
    load_id: str
    error_logs: list[BulkLoadErrorLog]


class BulkLoadStatusResponse(SerialisableModel):
    overall_status: BulkLoadFeed
    failed_feeds: list[BulkLoadFeed] = []
    errors: BulkLoadErrors
