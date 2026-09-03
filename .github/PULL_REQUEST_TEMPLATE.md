## What does this change?

<!-- A PR should have enough detail to be understandable far in the future. e.g. what is the problem / why is the change needed, how does it solve it, and any questions or points of discussion. -->

### Checklist

- [ ] Does this change the display model the catalogue API returns? If so, regenerate the test documents under `catalogue_graph/document_generators/test_documents`. After merge, `sync-test-documents.yml` copies them into `wellcomecollection/catalogue-api` as an auto-PR, where OpenAPI contract tests validate the published spec against them; expect that PR to fail CI until the spec (`catalogue-api/reference/catalogue.yaml`) documents the new shape.

## How to test

<!-- Provide instructions to help others verify the change. This could take the form of "On PROD, do X and witness Y. On this branch, do X and witness Z." -->

## How can we measure success?

<!-- Do you expect errors to decrease? Do you expect user journeys to be simplified? What can be used to prove this? A filtered view of logs or analytics, etc? -->

## Have we considered potential risks?

<!-- What are the potential risks and how can they be mitigated? Does an error require an alarm? -->
