# Relation Embedder Router
## What does it do?

The router extracts any collectionPath values from incoming documents and notifies the embedder that there is a 
collectionPath to process.

```mermaid
sequenceDiagram
    participant Upstream
    participant Router
    participant Embedder
    participant Downstream

    Upstream ->> Router: A doc with collectionPath
    Router ->> Embedder: Here's a collectionPath for you
    Note over Embedder: modify all docs within that path's sphere of influence 
    Embedder ->> Downstream: process all these docs
```

If a document has no collectionPath, it is simply passed on to the works topic.
```mermaid

sequenceDiagram
    participant Upstream
    participant Router
    participant Embedder
    participant Downstream

    Upstream ->> Router: A doc with no collectionPath
    Note over Embedder: I'm not involved
    Router ->> Downstream: process this doc

```

## Why?

Unlike other pipeline stages, the relation embedder does not operate on the document currently 
passing through the pipeline, but on *all* documents with which that document has a relationship.

Because of this, the message sent to the embedder is the *content of the collectionPath*, rather 
than a way to identify the document in question.
