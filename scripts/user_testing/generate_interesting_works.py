# /// script
# requires-python = ">=3.12"
# dependencies = ["elasticsearch>=8", "boto3"]
# ///
"""Generate the interesting-works CSV for migration user testing.

Selects works by stable source keys (RefNo, priref, GUID, class queries) and
resolves canonical ids and links at generation time, because Axiell-only
canonical ids re-mint on every id-minter respin. Run it AFTER the round's
reindex, against that round's pipeline date:

    AWS_PROFILE=platform-developer uv run generate_interesting_works.py \
        --pipeline-date 2026-07-03 --out interesting-works.csv

The pipeline_storage secrets need platform-developer; pass the profile via
AWS_PROFILE or --profile.

Each row: class, source_key, title, canonical_id, url, what_to_look_at.
Curated entries keep their stable keys in CURATED below; class samples are
drawn fresh from the index each run. Extend by adding CURATED rows or a new
entry in CLASS_QUERIES.
"""

import argparse
import re
import csv
import json
import random

import boto3
import elasticsearch

WORK_URL = "https://wellcomecollection.org/works/{}"
SAMPLE_SIZE = 5
# Deterministic sampling so re-runs against the same index agree.
random.seed(6631)

# Hand-picked records with stable keys and a specific thing to check.
CURATED = [
    {
        "class": "sierra-axiell-merge",
        "key": "GC179",
        "query": {"term": {"query.identifiers.value": "GC179"}},
        "what_to_look_at": "Sierra and Axiell records merged into one work; check single work, no duplicate",
    },
    {
        "class": "resource-guides-absent",
        "key": "Archives and Manuscripts Resource Guide",
        "query": {"match_phrase": {"query.title": "Archives and Manuscripts Resource Guide"}},
        "what_to_look_at": "Resource guides should NOT appear in search; zero hits expected (deleted or suppressed at source)",
        "expect_absent": True,
    },
]

# Axiell (and other UUID-keyed archive) works, by source identifier shape;
# the indexed query fields carry no identifier scheme, only values.
UUID_SOURCE = {
    "regexp": {
        "query.sourceIdentifier.value": "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }
}

# Class queries sampled at generation time.
CLASS_QUERIES = {
    "lcsh-subjects": {
        "query": {
            "bool": {
                "must": [UUID_SOURCE, {"exists": {"field": "query.subjects.concepts.label"}}]
            }
        },
        "what_to_look_at": "Subject labels read clean (no vocabulary markers); subject links navigate",
    },
    "structured-dates": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"exists": {"field": "query.production.label"}}]}
        },
        "what_to_look_at": "Production dates display and date filtering finds the work",
    },
    "deep-hierarchy": {
        "query": {
            "bool": {
                "must": [UUID_SOURCE, {"wildcard": {"query.collectionPath.path": "*/*/*/*/*"}}]
            }
        },
        "what_to_look_at": "Archive tree browses to the top and back down; no missing intermediate levels",
    },
    "digitised-with-images": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"exists": {"field": "query.images.id"}}]}
        },
        "what_to_look_at": "Digitised items open in the viewer from the archive work",
    },
    "reference-number-search": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"exists": {"field": "query.referenceNumber"}}]}
        },
        "use_reference_number": True,
        "what_to_look_at": "Type the reference number (source_key) into site search; this work should be the top result, with and without spaces",
    },
    "contributors": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"exists": {"field": "query.contributors.agent.label"}}]}
        },
        "what_to_look_at": "Contributor names display and their links lead to a sensible person page with this work listed",
    },
    "languages": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"exists": {"field": "query.languages.label"}}]}
        },
        "what_to_look_at": "Language displays and the language filter finds the work",
    },
    "alternative-titles": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"exists": {"field": "query.alternativeTitles"}}]}
        },
        "what_to_look_at": "Searching the variant title finds the work; both titles visible on the work page",
    },
    "closed-until-notes": {
        "query": {
            "bool": {"must": [UUID_SOURCE, {"match_phrase": {"query.notes.contents": "Closed until"}}]}
        },
        "what_to_look_at": "Access status should show as Closed with the closure date, not Restricted or Open (the round 2 load lost Closed; the round 3 load should carry it)",
    },
}


def es_client(pipeline_date: str, profile: str | None = None) -> elasticsearch.Elasticsearch:
    session = boto3.Session(profile_name=profile) if profile else boto3.Session()
    sm = session.client("secretsmanager", region_name="eu-west-1")
    prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"

    def secret(name: str) -> str:
        return sm.get_secret_value(SecretId=f"{prefix}/{name}")["SecretString"]

    return elasticsearch.Elasticsearch(
        f"https://{secret('public_host')}:443",
        basic_auth=(secret("read_only/es_username"), secret("read_only/es_password")),
        request_timeout=60,
    )


def rows_for(es, index, entry, cls, sample=False):
    r = es.search(
        index=index,
        size=50 if sample else 5,
        query=entry["query"],
        source=["display.title", "query.identifiers.value", "query.referenceNumber"],
        # Stable candidate order so the seeded sample is reproducible.
        sort=[{"query.id": "asc"}],
    )
    hits = r["hits"]["hits"]
    if entry.get("expect_absent"):
        return [
            {
                "class": cls,
                "source_key": entry["key"],
                "title": "(search phrase)",
                "canonical_id": "",
                "url": "",
                "what_to_look_at": entry["what_to_look_at"]
                + f" (currently {len(hits)} hits)",
            }
        ]
    if sample and len(hits) > SAMPLE_SIZE:
        hits = random.sample(hits, SAMPLE_SIZE)
    out = []
    uuid_re = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    for h in hits:
        # _source keeps identifiers as one flat value list; the GUID is the stable key.
        # Source filtering returns the literal dotted key "identifiers.value".
        q = h["_source"].get("query", {})
        values = q.get("identifiers.value") or q.get("identifiers", {}).get("value", [])
        others = [v for v in values if v != h["_id"]]
        source_id = next((v for v in others if uuid_re.match(v)), others[0] if others else "")
        if entry.get("use_reference_number"):
            source_id = q.get("referenceNumber", source_id)
        out.append(
            {
                "class": cls,
                "source_key": entry.get("key") or source_id,
                "title": (h["_source"].get("display", {}).get("title") or "")[:100],
                "canonical_id": h["_id"],
                "url": WORK_URL.format(h["_id"]),
                "what_to_look_at": entry["what_to_look_at"],
            }
        )
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pipeline-date", required=True)
    ap.add_argument("--out", default="interesting-works.csv")
    ap.add_argument("--profile", default=None, help="AWS profile for the secrets lookup (or set AWS_PROFILE)")
    args = ap.parse_args()

    es = es_client(args.pipeline_date, args.profile)
    index = f"works-indexed-{args.pipeline_date}"

    rows = []
    for entry in CURATED:
        rows += rows_for(es, index, entry, entry["class"])
    for cls, entry in CLASS_QUERIES.items():
        rows += rows_for(es, index, entry, cls, sample=True)

    with open(args.out, "w", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=["class", "source_key", "title", "canonical_id", "url", "what_to_look_at"],
        )
        w.writeheader()
        w.writerows(rows)
    print(f"{len(rows)} rows written to {args.out}")
    counts = {}
    for r in rows:
        counts[r["class"]] = counts.get(r["class"], 0) + 1
    print(json.dumps(counts, indent=1))


if __name__ == "__main__":
    main()
