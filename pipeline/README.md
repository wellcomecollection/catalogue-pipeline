# The pipeline

## The Works pipeline

How this all fits together

```mermaid
flowchart TB
 start([Source Data Service]) --> Adapter
    storage[(Storage)]

    subgraph Adapter
        direction LR
        adapter --> storage
    end

    works_source[(works_source)]
    subgraph Transformer
        direction LR
        transformer --> works_source
    end

    id_database[(id database)]
    works_identified[(works_identified)]
    subgraph ID_Minter
        direction LR
        id_minter <--> id_database
        id_minter --> works_identified
    end

    matchergraph[(matcher graph)]

    subgraph MatcherMerger
        direction LR
        matcher <--> matchergraph
        matcher --> merger
        merger --> works_denormalised
    end

    works_denormalised[(works_denormalised)]
    subgraph RelationEmbedder
        direction LR
        path_concatenator <--> works_denormalised
        path_concatenator --> batcher
        batcher --> relation_embedder
        relation_embedder --> works_denormalised
    end

    works_indexed[(works_indexed)]
    subgraph Ingestor
        direction LR
        ingestor --> works_indexed
    end

    Adapter --> Transformer
    Transformer --> ID_Minter
    ID_Minter --> MatcherMerger
    MatcherMerger --> RelationEmbedder
    RelationEmbedder --> Ingestor

```

Individual stages:
* [CALM adapter](../calm_adapter/README.md)
* [EBSCO adapter](../ebsco_adapter/README.md)
* [METS adapter](../mets_adapter/README.md)
* [SIERRA adapter](../sierra_adapter/README.md)
* [TEI adapter](../tei_adapter/README.md)
* [transformers](./transformer/)
* [id_minter](./id_minter/README.md)
* [matcher](./matcher_merger/matcher/README.md)
* [merger](./matcher_merger/merger/README.md)
* [path_concatenator](./relation_embedder/path_concatenator/README.md)
* [batcher](./relation_embedder/batcher/README.md)
* [relation_embedder](./relation_embedder/relation_embedder/README.md)
* [ingestor](./ingestor/)


