# Fixture regeneration log

- 2026-01-19T13:46:28+00:00 (brychtas): Initial fixtures
- 2026-06-17T13:31:12+00:00 (brychtas): One of the tests started failing due to natural drift between fixtures and
  production graph.
- 2026-08-06T11:37:11+00:00 (agnesgaroux): One of the tests (concept_people) started failing due to natural drift between fixtures and production graph. (fixtures: concept_people)
- 2026-09-29T10:20:56+00:00 (kennyr): Regenerated against the 2026-07-03 graph after the pipeline switch (wellcomecollection/platform#6541); the legacy fixtures held orphan concepts and arbitrary picks among duplicate concepts (fixtures: concept_types, concept_frequent_collaborators, concept_same_as, concept_related_to, concept_related_topics, concept_fields_of_work, concept_narrower_than, concept_broader_than, concept_people, concept_has_founder, work_ancestors)
- 2026-10-06T08:39:42+00:00 (kennyr): Fixtures keyed per graph date so CI follows the production graph date; regenerated against the 2026-07-03 graph after the full concept edges re-extract on 2026-10-06 raised concept_people drift to 60% (graph: 2026-07-03; fixtures: concept_types, concept_frequent_collaborators, concept_same_as, concept_related_to, concept_related_topics, concept_fields_of_work, concept_narrower_than, concept_broader_than, concept_people, concept_has_founder, work_ancestors)
- 2026-10-06T08:47:09+00:00 (kennyr): Fixtures for the 2026-09-30 graph ahead of the pipeline switch (wellcomecollection/platform#6743) (graph: 2026-09-30; fixtures: concept_types, concept_frequent_collaborators, concept_same_as, concept_related_to, concept_related_topics, concept_fields_of_work, concept_narrower_than, concept_broader_than, concept_people, concept_has_founder, work_ancestors)
