---
applyTo: "catalogue_graph/**/*.py"
description: "Conventions for the catalogue_graph Python project: adapter step pattern, structlog logging, dependency pinning, and where things live."
---

# catalogue_graph conventions

Largest Python project in the repo. Reads/writes Iceberg + Neptune + Elasticsearch
and ships as Lambda functions and ECS tasks from a single shared image.

Always `cd catalogue_graph/` before any `uv run …` command — this project has
its own `uv.lock`, `.python-version` (3.12), and virtualenv.

## Layout cheat sheet

- `src/adapters/oai_pmh/` — generic OAI-PMH adapter framework (runtime base
  class, reusable steps in `steps/loader.py|reloader.py|trigger.py`).
- `src/adapters/{axiell,folio}/` — concrete OAI-PMH adapters that extend
  `OAIPMHRuntimeConfig` and wrap the generic steps.
- `src/adapters/ebsco/`, `src/adapters/marc/`, `src/adapters/transformers/` —
  source-specific code.
- `src/utils/` — shared helpers (`logger.py`, `steps.py`, `manifests.py`, …).
- `src/clients/` — Neptune, Elasticsearch and metric clients.
- `src/ingestor/`, `src/graph/`, `src/id_minter/` — pipeline stages.
- `tests/` mirrors `src/`. `pythonpath = ["src", "infra/lambda_extensions"]`
  so import as `from adapters.foo …`, **not** `from src.adapters.foo …`.

## Adapter / step pattern

When adding or modifying an OAI-PMH adapter:

- Subclass `OAIPMHRuntimeConfig` (see
  [adapters/folio/runtime.py](../../catalogue_graph/src/adapters/folio/runtime.py))
  and expose a module-level singleton (e.g. `FOLIO_CONFIG`).
- Per-step modules live under `adapters/<name>/steps/` and are thin wrappers
  around `adapters.oai_pmh.steps.{loader,reloader,trigger}` — re-export
  `build_runtime`, `handler`, `lambda_handler`, `main`. Don't duplicate the
  generic logic; extend the base instead.
- Step config goes in a `pydantic.BaseModel` subclass named `<Step>StepConfig`.
- Every entrypoint module exposes both `lambda_handler(event, context)` and a
  `main()` CLI entry point guarded by `if __name__ == "__main__":`.

## Logging

- Always use structlog, never `logging` directly:
  ```python
  import structlog
  logger = structlog.get_logger(__name__)
  ```
- At each entrypoint (lambda or CLI), call `setup_logging(ExecutionContext(...))`
  from `utils.logger` with `trace_id=get_trace_id(context)` and a
  `pipeline_step` name. See
  [src/default.py](../../catalogue_graph/src/default.py) for the minimal pattern.
- Log structured key/value pairs (`logger.info("...", window=window)`), not
  formatted strings.

## Step Functions integration

ECS tasks that participate in Step Functions use
`utils.steps.run_ecs_handler(...)` which handles `--task-token`,
`send_task_success`/`send_task_failure`, and event validation via a Pydantic
model. Don't reinvent this glue.

## Manifests

Step output manifests (NDJSON files in S3 consumed by downstream Map states)
go through `utils.manifests.ManifestWriter`. Subclass it and implement
`_make_batch_line` rather than writing NDJSON by hand. Keep batches under the
256 KB Step Functions item limit.

## Dependencies & pinning

- `pydantic` is pinned `>=2.11.7,<2.12.0` because of a pyiceberg
  `TableMetadata` validation incompatibility — do not bump it without
  verifying pyiceberg compatibility.
- `elasticsearch` is pinned `>=8.11,<8.13` — match the deployed cluster
  version.
- `pyarrow` and `pyiceberg` are why the project stays on Python 3.12. Don't
  raise `requires-python` without checking wheel availability.
- Add new deps with `uv add …`; dev-only deps with `uv add --dev …`.
- The `oai-pmh-client` dep is a git source pinned to a tag in
  `[tool.uv.sources]` — bump the tag, not the URL.

## Testing

- `pytest` config lives in `pyproject.toml`. Default run **excludes**
  `integration` and `database` markers (`-m 'not integration'`).
- Use `freezegun` for time, `hypothesis` for property tests, `pytest-bdd` for
  the gherkin suites under `tests/gherkin_steps/`.
- Scope test runs to the area you changed — a full `uv run pytest` is slow:
  ```bash
  uv run pytest tests/adapters/folio -q
  uv run mypy src/adapters/folio
  ```

## Type checking

mypy runs in strict mode (`disallow_untyped_defs`, `warn_return_any`, …).
New/changed functions need full annotations. External libs without stubs are
listed under `[[tool.mypy.overrides]]` in `pyproject.toml`; add to that list
rather than sprinkling `# type: ignore`.

## Don'ts

- Don't import from `src.…` — the `src/` directory is on `pythonpath`, so
  imports start at `adapters.…`, `utils.…`, `clients.…`, etc.
- Don't add a new logging library or call `logging.getLogger` — structlog is
  the standard here.
- Don't write step orchestration glue (task tokens, manifest NDJSON, OAI-PMH
  loader loops) from scratch when a base class or helper already exists.
