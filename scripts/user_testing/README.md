# User-testing artefacts

`generate_interesting_works.py` builds the interesting-works CSV that collections staff use when testing a migration round through the toggle preview (wellcomecollection/platform#6631).

Canonical work ids for Axiell-only works re-mint on every id-minter respin, so the CSV is a per-round artefact: run the script after the round's reindex, against that round's pipeline date, and attach the output to the round's user-testing issue. The script itself holds the durable part, the selection of record classes and hand-picked records by stable source keys (GUID, RefNo, search phrase).

    AWS_PROFILE=platform-developer uv run generate_interesting_works.py \
        --pipeline-date 2026-07-03 --out interesting-works.csv

Classes cover subjects, dates, hierarchy, merges, digitised images, reference-number search, contributors, languages, variant titles and closed-access notes; genres are omitted because Axiell works currently carry none. Extend it by adding a `CURATED` row (a stable key plus what to check) or a new `CLASS_QUERIES` entry (an Elasticsearch query over the `query.*` fields plus what to check). Note the `digitised-with-images` class doubles as evidence for the b-number load (wellcomecollection/platform#6542): it should grow substantially once b numbers connect archive works to their METS images.
