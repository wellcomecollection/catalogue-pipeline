# AGENTS.md

Instructions for AI coding agents working in this repository.

The Wellcome Collection catalogue pipeline: adapters that fetch records from
source systems, a transformation pipeline that produces a common model, and
ingestors that populate Elasticsearch indexes powering the catalogue API.

Start with [README.md](README.md) and [docs/developers.md](docs/developers.md)
for the high-level design. Don't duplicate that material here.

## Repository layout

This is a polyglot monorepo. Each top-level area is largely independent — there
is no workspace-wide build.

- **Scala** (SBT, root `build.sbt`): `common/`, `pipeline/`, `sierra_adapter/`,
  `mets_adapter/`, `tei_adapter/`, `calm_adapter/`, `reindexer/`. Built with
  scripts under `builds/`.
- **Python** (UV, one project per directory with its own `pyproject.toml`):
  - `catalogue_graph/` — graph + ingestor pipeline, also contains the EBSCO,
    FOLIO, Axiell and OAI-PMH adapters under `src/adapters/` (largest Python
    project; see [catalogue_graph/README.md](catalogue_graph/README.md))
  - `pipeline/inferrer/{aspect_ratio_inferrer,feature_inferrer,palette_inferrer,common}/`
  - `reindexer/scripts/`, `scripts/{suppress_miro,miro_links,mimsy_dump,es_index_comparison}/`

When working in a Python project, `cd` into that project's directory before
running any `uv` command — each has its own `uv.lock`, `.python-version` and
virtualenv.

## Python conventions

- **Always use `uv`.** Never use `pip`, `poetry`, or `pipenv`. Dev dependencies
  live in `[dependency-groups]` in `pyproject.toml`.
- Project layout follows
  [RFC 071-python_builds](https://github.com/wellcomecollection/docs/tree/main/rfcs/071-python_builds).
- Build artefacts (Lambda zips, container images) are produced via
  `uv export --no-dev --format=requirements-txt` and
  `uv pip install --python-platform x86_64-manylinux2014 --target <dir> --only-binary=:all:`.
  See `builds/` and each project's `Dockerfile`.

### Before finalising a Python change

Run from inside the affected project directory:

```bash
uv run pytest
uv run mypy .
uv run ruff format
uv run ruff check --fix
```

Scope `pytest`/`mypy` paths to the area you changed when the project is large
(e.g. `catalogue_graph/`); a full run can be slow.

## Scala conventions

- SBT project at the repo root. Sub-projects are wired up in `build.sbt` via
  `setupProject(...)`; tests share `compile->compile;test->test` config.
- Use the helper scripts in `builds/` (e.g. `run_sbt_tests.sh`,
  `run_sbt_task_in_docker.sh`) rather than invoking `sbt` directly when the task
  needs the project's docker dependencies.
- Formatting runs via `builds/run_formatting.sh`.

## CI

GitHub Actions live in `.github/workflows/`; some Scala projects use Buildkite
instead (badges in [README.md](README.md)). Python checks use the shared
`wellcomecollection/.github/.github/actions/python_check@main` action — match
its commands locally (the four `uv run` commands above) to keep CI green.

## Don'ts

- Don't add docstrings, comments, or type annotations to code you didn't change.
- Don't introduce a new Python tool (black, isort, flake8, poetry…) — `ruff`
  and `mypy` cover formatting, linting and typing.
- Don't commit changes to `uv.lock` unless you intentionally changed
  dependencies.
