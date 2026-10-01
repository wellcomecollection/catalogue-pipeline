import json

import boto3
import pyarrow as pa
import pyarrow.parquet as pq
import pytest
from click.testing import CliRunner
from moto import mock_aws

import restore_sierra_bibs_from_snapshot as restore
from backstage_changed_ids import classify_one, compare
from sierra_vhs_snapshot import (
    BUCKET,
    TABLE_NAME,
    changed_marc_tags,
    iter_snapshot_batches,
    iter_snapshot_rows,
    read_id_file,
)

ID = "1234567"


def bib(record_id=ID, title="Old title", modified="2026-09-01T00:00:00Z", tag245="Old"):
    data = {
        "id": record_id,
        "updatedDate": modified,
        "title": title,
        "varFields": [
            {"marcTag": "245", "subfields": [{"tag": "a", "content": tag245}]},
            {"marcTag": "650", "subfields": [{"tag": "a", "content": "Medicine"}]},
        ],
    }
    return {"id": record_id, "data": json.dumps(data), "modifiedDate": modified}


def body(record_id=ID, bib_record=None, items=None, modified_time=None):
    bib_record = bib_record if bib_record is not None else bib(record_id)
    return {
        "sierraId": record_id,
        "maybeBibRecord": bib_record,
        "itemRecords": items or {},
        "holdingsRecords": {},
        "orderRecords": {},
        "modifiedTime": modified_time or bib_record["modifiedDate"],
    }


SNAPSHOT_BODY = body()
BACKSTAGE_BIB = bib(title="New title", modified="2026-10-02T00:00:00Z", tag245="New")
ITEM = {
    "7654321": {"id": "7654321", "data": "{}", "modifiedDate": "2026-10-03T00:00:00Z"}
}
CURRENT_BODY = body(
    bib_record=BACKSTAGE_BIB, items=ITEM, modified_time="2026-10-03T00:00:00Z"
)


# changed-id comparison and classification


def test_compare_splits_ids_by_version_movement():
    live = {"a": 2, "b": 5, "c": 1, "n": 1}
    snap = {"a": 2, "b": 4, "c": 3, "m": 1}
    result = compare(live, snap)
    assert result.changed == ["b"]
    assert result.moved_backward == ["c"]
    assert result.new == ["n"]
    assert result.missing == ["m"]


def test_classify_bib_change():
    result = classify_one(CURRENT_BODY, SNAPSHOT_BODY)
    assert result["bib_changed"] is True
    assert result["items_changed"] is True
    assert "245" in result["changed_tags"].split()
    assert "650" not in result["changed_tags"].split()


def test_classify_item_only_change():
    current = body(items=ITEM, modified_time="2026-10-03T00:00:00Z")
    result = classify_one(current, SNAPSHOT_BODY)
    assert result["bib_changed"] is False
    assert result["items_changed"] is True
    assert result["changed_tags"] == ""


def test_changed_marc_tags_includes_top_level_fields():
    tags = changed_marc_tags(
        SNAPSHOT_BODY["maybeBibRecord"]["data"], BACKSTAGE_BIB["data"]
    )
    assert tags == ["245", "title"]


def test_read_id_file_rejects_b_numbers(tmp_path):
    path = tmp_path / "ids.txt"
    path.write_text("1234567\n\nb12345678\n")
    with pytest.raises(ValueError):
        read_id_file(str(path))


def test_iter_snapshot_rows_reads_only_wanted_ids(tmp_path):
    path = tmp_path / "snap.parquet"
    table = pa.table({"id": ["1", "2", "3"], "content": ["a", "b", "c"]})
    pq.write_table(table, path, row_group_size=1)
    rows = list(iter_snapshot_rows(pq.ParquetFile(path), ["3", "1"], ["content"]))
    assert sorted((r["id"], r["content"]) for r in rows) == [("1", "a"), ("3", "c")]


# restore body construction and planning


def row(version=5, key=f"{ID}/5/old.json", bucket=BUCKET):
    return {"id": ID, "version": version, "payload": {"bucket": bucket, "key": key}}


def test_restored_body_replaces_bib_and_keeps_subrecords():
    restored = restore.build_restored_body(CURRENT_BODY, SNAPSHOT_BODY)
    assert restored["maybeBibRecord"] == SNAPSHOT_BODY["maybeBibRecord"]
    assert restored["itemRecords"] == ITEM
    assert restored["modifiedTime"] == "2026-10-03T00:00:00Z"
    assert CURRENT_BODY["maybeBibRecord"] == BACKSTAGE_BIB, "input not mutated"


def test_restored_body_modified_time_never_precedes_bib():
    current = dict(
        CURRENT_BODY, maybeBibRecord=None, modifiedTime="2026-08-01T00:00:00Z"
    )
    restored = restore.build_restored_body(current, SNAPSHOT_BODY)
    assert restored["modifiedTime"] == SNAPSHOT_BODY["maybeBibRecord"]["modifiedDate"]


def test_plan_bumps_version_and_uses_new_key():
    plan = restore.plan_one(ID, json.dumps(SNAPSHOT_BODY), row(), CURRENT_BODY)
    assert plan.outcome == restore.RESTORE
    assert plan.new_version == 6
    assert plan.new_key.startswith(f"{ID}/6/") and plan.new_key.endswith(".json")
    assert plan.changed_tags == ["245", "title"]


def test_plan_skips_when_bib_already_matches():
    current = body(items=ITEM, modified_time="2026-10-03T00:00:00Z")
    plan = restore.plan_one(ID, json.dumps(SNAPSHOT_BODY), row(), current)
    assert plan.outcome == restore.UNCHANGED


def test_plan_refuses_id_missing_from_snapshot():
    plan = restore.plan_one(ID, None, row(), CURRENT_BODY)
    assert plan.outcome == restore.REFUSED


def test_plan_refuses_when_current_bib_is_older_than_snapshot():
    current = body(bib_record=bib(title="Older", modified="2025-01-01T00:00:00Z"))
    plan = restore.plan_one(ID, json.dumps(SNAPSHOT_BODY), row(), current)
    assert plan.outcome == restore.REFUSED


# restore against moto


@pytest.fixture
def aws(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "eu-west-1")
    with mock_aws():
        s3 = boto3.client("s3", region_name="eu-west-1")
        s3.create_bucket(
            Bucket=BUCKET,
            CreateBucketConfiguration={"LocationConstraint": "eu-west-1"},
        )
        dynamodb = boto3.client("dynamodb", region_name="eu-west-1")
        dynamodb.create_table(
            TableName=TABLE_NAME,
            KeySchema=[{"AttributeName": "id", "KeyType": "HASH"}],
            AttributeDefinitions=[{"AttributeName": "id", "AttributeType": "S"}],
            BillingMode="PAY_PER_REQUEST",
        )
        key = f"{ID}/5/old.json"
        s3.put_object(Bucket=BUCKET, Key=key, Body=json.dumps(CURRENT_BODY))
        dynamodb.put_item(
            TableName=TABLE_NAME,
            Item={
                "id": {"S": ID},
                "version": {"N": "5"},
                "payload": {"M": {"bucket": {"S": BUCKET}, "key": {"S": key}}},
            },
        )
        yield dynamodb, s3


def _row(dynamodb):
    return dynamodb.get_item(TableName=TABLE_NAME, Key={"id": {"S": ID}})["Item"]


def _keys(s3):
    return sorted(
        o["Key"] for o in s3.list_objects_v2(Bucket=BUCKET).get("Contents", [])
    )


def test_dry_run_writes_nothing(aws):
    dynamodb, s3 = aws
    before_row, before_keys = _row(dynamodb), _keys(s3)
    plans = restore.run(
        [ID], {ID: json.dumps(SNAPSHOT_BODY)}, dynamodb, s3, execute=False
    )
    assert [p.outcome for p in plans] == [restore.RESTORE]
    assert _row(dynamodb) == before_row
    assert _keys(s3) == before_keys


def test_execute_writes_new_version(aws):
    dynamodb, s3 = aws
    [plan] = restore.run(
        [ID], {ID: json.dumps(SNAPSHOT_BODY)}, dynamodb, s3, execute=True
    )
    assert plan.outcome == restore.RESTORED

    item = _row(dynamodb)
    assert item["version"]["N"] == "6"
    new_key = item["payload"]["M"]["key"]["S"]
    assert new_key == plan.new_key and new_key.startswith(f"{ID}/6/")
    assert item["payload"]["M"]["bucket"]["S"] == BUCKET

    written = json.loads(s3.get_object(Bucket=BUCKET, Key=new_key)["Body"].read())
    assert written["maybeBibRecord"] == SNAPSHOT_BODY["maybeBibRecord"]
    assert written["itemRecords"] == ITEM
    assert f"{ID}/5/old.json" in _keys(s3), "old version left in place"


def test_execute_reports_conflict_when_row_moves(aws):
    dynamodb, s3 = aws
    row_, current = restore.read_current(dynamodb, s3, TABLE_NAME, ID)
    plan = restore.plan_one(ID, json.dumps(SNAPSHOT_BODY), row_, current)

    # A merger write lands between our read and our write.
    dynamodb.update_item(
        TableName=TABLE_NAME,
        Key={"id": {"S": ID}},
        UpdateExpression="SET version = :v",
        ExpressionAttributeValues={":v": {"N": "6"}},
    )
    result = restore.apply_plan(dynamodb, s3, TABLE_NAME, plan)
    assert result.outcome == restore.CONFLICT
    item = _row(dynamodb)
    assert item["version"]["N"] == "6"
    assert item["payload"]["M"]["key"]["S"] == f"{ID}/5/old.json"


def test_execute_skips_unchanged(aws):
    dynamodb, s3 = aws
    snapshot = json.dumps(body(bib_record=BACKSTAGE_BIB))
    before = _keys(s3)
    [plan] = restore.run([ID], {ID: snapshot}, dynamodb, s3, execute=True)
    assert plan.outcome == restore.UNCHANGED
    assert _keys(s3) == before


def test_cli_defaults_to_dry_run(aws, tmp_path, monkeypatch):
    dynamodb, s3 = aws
    snap = tmp_path / "snap.parquet"
    pq.write_table(pa.table({"id": [ID], "content": [json.dumps(SNAPSHOT_BODY)]}), snap)
    ids = tmp_path / "ids.txt"
    ids.write_text(f"{ID}\n9999999\n")
    output = tmp_path / "restored.txt"
    before_row, before_keys = _row(dynamodb), _keys(s3)

    result = CliRunner().invoke(
        restore.main,
        ["--ids-file", str(ids), "--snapshot", str(snap), "--output", str(output)],
    )

    assert result.exit_code == 0, result.output
    assert "v5 -> v6" in result.output
    assert "9999999  refused" in result.output
    assert "Dry run" in result.output
    assert _row(dynamodb) == before_row
    assert _keys(s3) == before_keys
    assert not output.exists()


def test_iter_snapshot_batches_bounds_batch_size(tmp_path):
    path = tmp_path / "snap.parquet"
    pq.write_table(pa.table({"id": ["1", "2", "3"], "content": ["a", "b", "c"]}), path)
    batches = list(iter_snapshot_batches(pq.ParquetFile(path), ["1", "2", "3"], 2))
    assert [len(b) for b in batches] == [2, 1]


def test_run_from_snapshot_refuses_ids_missing_from_snapshot(aws, tmp_path):
    dynamodb, s3 = aws
    path = tmp_path / "snap.parquet"
    pq.write_table(pa.table({"id": [ID], "content": [json.dumps(SNAPSHOT_BODY)]}), path)
    before = _keys(s3)
    plans = restore.run_from_snapshot(
        ["9999999", ID], pq.ParquetFile(path), dynamodb, s3, execute=False
    )
    assert [(p.id, p.outcome) for p in plans] == [
        ("9999999", restore.REFUSED),
        (ID, restore.RESTORE),
    ]
    assert _keys(s3) == before
