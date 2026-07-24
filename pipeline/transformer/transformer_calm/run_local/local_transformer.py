#!/usr/bin/env python3

import argparse
import json
import os
import re
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Optional

import boto3


def _project_dir() -> Path:
    return Path(__file__).resolve().parent.parent


def _repo_root() -> Path:
    return _project_dir().parent.parent.parent


def _get_session(aws_region: str, aws_profile: Optional[str]) -> boto3.Session:
    kwargs = {"region_name": aws_region}
    if aws_profile:
        kwargs["profile_name"] = aws_profile
    return boto3.Session(**kwargs)


def _read_secret_value(*, session: boto3.Session, secret_id: str) -> str:
    client = session.client("secretsmanager")
    response = client.get_secret_value(SecretId=secret_id)
    return response["SecretString"]


def fetch_es_env(
    *,
    pipeline_date: str,
    index_name: str,
    es_host: str,
    output_env_file: Path,
    aws_region: str,
    aws_profile: Optional[str],
) -> None:
    session = _get_session(aws_region=aws_region, aws_profile=aws_profile)

    if es_host == "local":
        es_host = "elasticsearch"
        es_port = "9200"
        es_protocol = "http"
        es_apikey = ""
    else:
        es_host = _read_secret_value(
            session=session,
            secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/public_host",
        )
        es_port = _read_secret_value(
            session=session, secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/port"
        )
        es_protocol = _read_secret_value(
            session=session,
            secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/protocol",
        )
        es_apikey = _read_secret_value(
            session=session,
            secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/transformer/api_key",
        )
    session_credentials = session.get_credentials()
    if session_credentials is None:
        raise RuntimeError("Unable to resolve AWS credentials from the current session")
    frozen_credentials = session_credentials.get_frozen_credentials()

    output_env_file.write_text(
        "\n".join(
            [
                f"es_host={es_host}",
                f"es_port={es_port}",
                f"es_protocol={es_protocol}",
                f"es_apikey={es_apikey}",
                f"es_index={index_name}",
                f"AWS_ACCESS_KEY_ID={frozen_credentials.access_key}",
                f"AWS_SECRET_ACCESS_KEY={frozen_credentials.secret_key}",
                f"AWS_SESSION_TOKEN={frozen_credentials.token or ''}",
                f"AWS_REGION={aws_region}",
                f"AWS_DEFAULT_REGION={aws_region}",
                "",
            ]
        ),
        encoding="utf-8",
    )

    print(f"Wrote ES config to {output_env_file}")


def _load_env_file(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    if not path.exists():
        return env

    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        env[key.strip()] = value.strip()
    return env


def _ensure_local_es_index(*, env_file: Path, timeout_seconds: int = 120) -> None:
    env = _load_env_file(env_file)
    es_protocol = env.get("es_protocol", "http")
    es_host = env.get("es_host", "elasticsearch")
    es_host_for_local_checks = "localhost" if es_host == "elasticsearch" else es_host
    es_port = env.get("es_port", "9200")
    es_index = env.get("es_index")

    if not es_index:
        raise RuntimeError(f"Missing es_index in {env_file}")

    index_url = f"{es_protocol}://{es_host_for_local_checks}:{es_port}/{es_index}"
    health_url = f"{es_protocol}://{es_host_for_local_checks}:{es_port}/_cluster/health"
    context = ssl.create_default_context()
    headers = {"Content-Type": "application/json"}

    start_time = time.time()
    while True:
        try:
            req = urllib.request.Request(health_url, headers=headers, method="GET")
            with urllib.request.urlopen(req, context=context):
                break
        except Exception:
            if time.time() - start_time >= timeout_seconds:
                raise TimeoutError(
                    f"Timed out waiting for Elasticsearch health at {health_url}"
                )
            time.sleep(2)

    try:
        get_req = urllib.request.Request(index_url, headers=headers, method="GET")
        with urllib.request.urlopen(get_req, context=context) as response:
            if response.status == 200:
                print(f"Local Elasticsearch index already exists: {es_index}")
                return
    except urllib.error.HTTPError as err:
        if err.code != 404:
            raise

    create_req = urllib.request.Request(
        index_url,
        data=b"{}",
        headers=headers,
        method="PUT",
    )
    with urllib.request.urlopen(create_req, context=context) as response:
        if response.status not in (200, 201):
            raise RuntimeError(
                f"Failed to create local Elasticsearch index {es_index}: HTTP {response.status}"
            )
    print(f"Created local Elasticsearch index: {es_index}")


def command_start(args: argparse.Namespace) -> int:
    project_dir = _project_dir()
    env_file = project_dir / ".env.local"

    fetch_es_env(
        pipeline_date=args.pipeline_date,
        index_name=args.index_name,
        es_host=args.es_host,
        output_env_file=env_file,
        aws_region=args.aws_region,
        aws_profile=args.aws_profile,
    )

    sbt_script = _repo_root() / "builds" / "run_sbt_task_in_docker.sh"
    subprocess.run(
        [str(sbt_script), "project transformer_calm", "stage"],
        cwd=project_dir,
        check=True,
    )

    subprocess.run(
        [
            "docker",
            "compose",
            "-f",
            "local.docker-compose.yml",
            "up",
            "-d",
            "--build",
            "--force-recreate",
            "localstack",
            "localstack-setup",
            "elasticsearch",
            "calm-transformer",
        ],
        cwd=project_dir,
        check=True,
    )

    if args.es_host == "local":
        _ensure_local_es_index(env_file=env_file)

    print("Local CALM transformer is running.")
    print(
        "Next: enqueue IDs from a file with "
        "run_local/local_transformer.py enqueue <ids_file> [batch_size]"
    )

    if args.no_logs:
        return 0

    print("Streaming logs from calm-transformer (Ctrl+C to stop log tail)...")
    try:
        subprocess.run(
            [
                "docker",
                "compose",
                "-f",
                "local.docker-compose.yml",
                "logs",
                "-f",
                "calm-transformer",
            ],
            cwd=project_dir,
            check=True,
        )
    except KeyboardInterrupt:
        print("\nStopped log streaming; containers continue running.")
    return 0


def command_enqueue(args: argparse.Namespace) -> int:
    ids_file = Path(args.ids_file)
    if not ids_file.exists():
        raise FileNotFoundError(f"No such IDs file: {ids_file}")

    if args.batch_size <= 0:
        raise ValueError("batch_size must be a positive integer")

    calm_table_name = args.calm_table_name or os.environ.get(
        "CALM_TABLE_NAME", "vhs-calm-adapter"
    )

    real_session = _get_session(
        aws_region=args.aws_region,
        aws_profile=args.aws_profile,
    )
    dynamo = real_session.client("dynamodb")

    local_sqs = boto3.client(
        "sqs",
        region_name=args.aws_region,
        endpoint_url=args.queue_endpoint,
        aws_access_key_id="test",
        aws_secret_access_key="test",
    )

    sent_count = 0
    missing_count = 0
    batch: list[str] = []
    batch_number = 1

    def enqueue_id(calm_id: str) -> tuple[bool, Optional[str]]:
        response = dynamo.get_item(
            TableName=calm_table_name, Key={"id": {"S": calm_id}}
        )
        item = response.get("Item")
        if not item:
            return False, f"Skipping missing CALM ID: {calm_id}"

        payload_attr = item.get("payload", {}).get("M", {})
        bucket = payload_attr.get("bucket", {}).get("S")
        key = payload_attr.get("key", {}).get("S")
        version_str = item.get("version", {}).get("N")
        is_deleted = item.get("isDeleted", {}).get("BOOL", False)

        if not bucket or not key or not version_str:
            return (
                False,
                (
                    f"Skipping invalid CALM row for ID {calm_id}: "
                    "missing payload bucket/key/version"
                ),
            )

        message_payload = {
            "id": calm_id,
            "location": {"bucket": bucket, "key": key},
            "version": int(version_str),
            "isDeleted": bool(is_deleted),
        }

        wrapped_message = {"Message": json.dumps(message_payload)}
        local_sqs.send_message(
            QueueUrl=args.queue_url, MessageBody=json.dumps(wrapped_message)
        )
        return True, None

    def process_batch(current_batch: list[str], current_batch_number: int) -> None:
        nonlocal sent_count, missing_count
        print(f"Enqueueing batch {current_batch_number} ({len(current_batch)} IDs)")
        for calm_id in current_batch:
            sent, warning = enqueue_id(calm_id)
            if sent:
                sent_count += 1
            else:
                missing_count += 1
                print(warning, file=sys.stderr)

    for raw_line in ids_file.read_text(encoding="utf-8").splitlines():
        calm_id = raw_line.strip()
        if not calm_id:
            continue
        batch.append(calm_id)
        if len(batch) >= args.batch_size:
            process_batch(batch, batch_number)
            batch = []
            batch_number += 1

    if batch:
        process_batch(batch, batch_number)

    print(f"Done. Enqueued {sent_count} IDs to {args.queue_url}; missing IDs: {missing_count}")
    return 0


def command_verify_completion(args: argparse.Namespace) -> int:
    ids_file = Path(args.ids_file)
    if not ids_file.exists():
        raise FileNotFoundError(f"No such IDs file: {ids_file}")

    if args.timeout_seconds <= 0:
        raise ValueError("timeout_seconds must be a positive integer")
    if args.poll_seconds <= 0:
        raise ValueError("poll_seconds must be a positive integer")

    project_dir = _project_dir()
    env_file = project_dir / ".env.local"
    file_env = _load_env_file(env_file)
    runtime_env = os.environ.copy()
    runtime_env.update(file_env)

    required_env_vars = ["es_protocol", "es_host", "es_port", "es_index"]
    missing_env_vars = [key for key in required_env_vars if not runtime_env.get(key)]
    if missing_env_vars:
        missing = ", ".join(missing_env_vars)
        raise RuntimeError(f"Missing required env vars in {env_file}: {missing}")

    ids = [line.strip() for line in ids_file.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not ids:
        raise RuntimeError(f"IDs file contains no IDs: {ids_file}")

    local_sqs = boto3.client(
        "sqs",
        region_name=args.aws_region,
        endpoint_url=args.queue_endpoint,
        aws_access_key_id="test",
        aws_secret_access_key="test",
    )

    print("Waiting for queue to drain...")
    start_time = time.time()
    while True:
        attrs = local_sqs.get_queue_attributes(
            QueueUrl=args.queue_url,
            AttributeNames=[
                "ApproximateNumberOfMessages",
                "ApproximateNumberOfMessagesNotVisible",
            ],
        )["Attributes"]

        visible = int(attrs.get("ApproximateNumberOfMessages", "0"))
        in_flight = int(attrs.get("ApproximateNumberOfMessagesNotVisible", "0"))
        print(f"Queue status: visible={visible} in_flight={in_flight}")

        if visible == 0 and in_flight == 0:
            break

        if time.time() - start_time >= args.timeout_seconds:
            raise TimeoutError(
                f"Timed out waiting for queue to drain after {args.timeout_seconds}s"
            )
        time.sleep(args.poll_seconds)

    print(f"Queue drained; checking Elasticsearch index {runtime_env['es_index']} ...")

    doc_ids = [f"Work[calm-record-id/{calm_id}]" for calm_id in ids]
    missing_doc_ids: list[str] = []

    es_host = runtime_env["es_host"]
    es_host_for_local_checks = "localhost" if es_host == "elasticsearch" else es_host
    es_url = (
        f"{runtime_env['es_protocol']}://{es_host_for_local_checks}:"
        f"{runtime_env['es_port']}/{runtime_env['es_index']}/_mget"
    )
    headers = {"Content-Type": "application/json"}
    if runtime_env.get("es_apikey"):
        headers["Authorization"] = f"ApiKey {runtime_env['es_apikey']}"

    context = ssl.create_default_context()
    batch_size = 500
    for i in range(0, len(doc_ids), batch_size):
        chunk = doc_ids[i : i + batch_size]
        payload = json.dumps({"ids": chunk}).encode("utf-8")
        req = urllib.request.Request(
            es_url, data=payload, headers=headers, method="POST"
        )
        with urllib.request.urlopen(req, context=context) as response:
            body = json.loads(response.read().decode("utf-8"))
        for doc in body.get("docs", []):
            if not doc.get("found", False):
                doc_id = doc.get("_id", "")
                match = re.search(r"calm-record-id/([^\]]+)]", doc_id)
                missing_doc_ids.append(match.group(1) if match else (doc_id or "<unknown>"))

    found_docs = len(doc_ids) - len(missing_doc_ids)
    print(
        "Elasticsearch coverage: "
        f"found={found_docs} expected={len(doc_ids)} missing={len(missing_doc_ids)}"
    )
    if missing_doc_ids:
        missing_ids_file = project_dir / "missing_ids.txt"
        missing_ids_file.write_text("\n".join(missing_doc_ids) + "\n", encoding="utf-8")
        print(f"Missing ids: see {missing_ids_file}")

    logs_proc = subprocess.run(
        [
            "docker",
            "compose",
            "-f",
            "local.docker-compose.yml",
            "logs",
            "calm-transformer",
        ],
        cwd=project_dir,
        check=True,
        capture_output=True,
        text=True,
    )
    logs_text = logs_proc.stdout + logs_proc.stderr
    error_pattern = re.compile(
        r"DecodePayloadError|StoreReadError|TransformerError|ERROR|Exception"
    )
    error_lines = [
        line for line in logs_text.splitlines() if error_pattern.search(line) is not None
    ]
    if error_lines:
        print("Potential transformer errors detected:")
        for line in error_lines[:40]:
            print(line)

    if missing_doc_ids or error_lines:
        return 1

    print(
        "Verification passed: queue is drained, no transformer errors found, "
        "and all IDs are indexed."
    )
    return 0


def _create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Local transformer_calm workflow CLI")
    subparsers = parser.add_subparsers(dest="command", required=True)

    fetch_parser = subparsers.add_parser(
        "fetch-es-env", help="Fetch ES vars from Secrets Manager into an env file"
    )
    fetch_parser.add_argument("pipeline_date")
    fetch_parser.add_argument("--index-name", required=True)
    fetch_parser.add_argument(
        "--es-host",
        choices=["local", "public"],
        default="local",
        help="Use local Docker Elasticsearch, or the deployed public Elasticsearch host.",
    )
    fetch_parser.add_argument("--output-env-file", default=".env.local")
    fetch_parser.add_argument("--aws-region", default=os.environ.get("AWS_REGION", "eu-west-1"))
    fetch_parser.add_argument("--aws-profile", default=os.environ.get("AWS_PROFILE", "platform-developer"))
    fetch_parser.set_defaults(
        handler=lambda a: (
            fetch_es_env(
                pipeline_date=a.pipeline_date,
                index_name=a.index_name,
                es_host=a.es_host,
                output_env_file=Path(a.output_env_file),
                aws_region=a.aws_region,
                aws_profile=a.aws_profile,
            ),
            0,
        )[1]
    )

    start_parser = subparsers.add_parser(
        "start", help="Fetch env, stage app, start local services, and follow logs"
    )
    start_parser.add_argument("pipeline_date")
    start_parser.add_argument("--index-name", required=True)
    start_parser.add_argument(
        "--es-host",
        choices=["local", "public"],
        default="local",
        help="Use local Docker Elasticsearch, or the deployed public Elasticsearch host.",
    )
    start_parser.add_argument("--no-logs", action="store_true")
    start_parser.add_argument("--aws-region", default=os.environ.get("AWS_REGION", "eu-west-1"))
    start_parser.add_argument("--aws-profile", default=os.environ.get("AWS_PROFILE", "platform-developer"))
    start_parser.set_defaults(handler=command_start)

    enqueue_parser = subparsers.add_parser(
        "enqueue", help="Enqueue line-separated CALM IDs with batching"
    )
    enqueue_parser.add_argument("ids_file")
    enqueue_parser.add_argument("batch_size", nargs="?", type=int, default=100)
    enqueue_parser.add_argument("--calm-table-name", default=os.environ.get("CALM_TABLE_NAME"))
    enqueue_parser.add_argument("--aws-region", default=os.environ.get("AWS_REGION", "eu-west-1"))
    enqueue_parser.add_argument("--aws-profile", default=os.environ.get("AWS_PROFILE", "platform-developer"))
    enqueue_parser.add_argument("--queue-url", default="http://localhost:4566/000000000000/calm-transformer-queue")
    enqueue_parser.add_argument("--queue-endpoint", default="http://localhost:4566")
    enqueue_parser.set_defaults(handler=command_enqueue)

    verify_parser = subparsers.add_parser(
        "verify-completion",
        help="Wait for queue drain and verify ES documents/log errors",
    )
    verify_parser.add_argument("ids_file")
    verify_parser.add_argument("timeout_seconds", nargs="?", type=int, default=600)
    verify_parser.add_argument("poll_seconds", nargs="?", type=int, default=10)
    verify_parser.add_argument("--aws-region", default=os.environ.get("AWS_REGION", "eu-west-1"))
    verify_parser.add_argument("--aws-profile", default=os.environ.get("AWS_PROFILE", "platform-developer"))
    verify_parser.add_argument("--queue-url", default="http://localhost:4566/000000000000/calm-transformer-queue")
    verify_parser.add_argument("--queue-endpoint", default="http://localhost:4566")
    verify_parser.set_defaults(handler=command_verify_completion)

    return parser


def main() -> int:
    parser = _create_parser()
    args = parser.parse_args()
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main())
