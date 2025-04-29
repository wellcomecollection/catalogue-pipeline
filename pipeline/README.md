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
    works_merged[(works_merged)]
    subgraph MatcherMerger
        direction LR
        matcher <--> matchergraph
        matcher --> merger
        merger --> works_merged
    end

    works_denormalised[(works_denormalised)]
    router{router}
    subgraph RelationEmbedder
        direction LR
        router --> path_concatenator
        path_concatenator <--> works_merged
        path_concatenator --> batcher
        router --> batcher
        batcher --> relation_embedder
        relation_embedder --> works_denormalised
        router --> works_denormalised
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

See individual stages for more detail:
* [matcher](./matcher/README.md)
* [merger](./merger/README.md)
* [path_concatenator](./relation_embedder/path_concatenator/README.md)


