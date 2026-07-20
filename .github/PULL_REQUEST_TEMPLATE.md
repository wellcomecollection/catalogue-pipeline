## What does this change?

<!-- What is the problem / why is the change needed, how does it solve it, and any points of discussion. -->

### Checklist

- [ ] Does this change the display model the catalogue API returns? If so, regenerate the test documents under `catalogue_graph/document_generators/test_documents`. catalogue-api pulls them in with `copy_test_documents.py`, and its OpenAPI contract tests validate the published spec against them, so stale fixtures let the spec drift.

## How to test

<!-- How can a reviewer verify the change? -->
