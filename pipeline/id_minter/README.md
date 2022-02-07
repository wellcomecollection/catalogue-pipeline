# Id Minter

This stage takes JSON objects containing diverse source ids and 
associates those ids with a canonical id in a common scheme.

The canonical ids created by this stage are stored in a database, 
associated with the original source identifer. When the same identifier
is encountered again, the canonical id from the database is returned.

When a novel source id is encountered, a new canonical id is 
minted.

See [SourceIdentifierEmbedder.scala](src/main/scala/weco/pipeline/id_minter/steps/SourceIdentifierEmbedder.scala)
for details on how this stage recognises an ID for which a canonical id,
and where it places the id in the resulting JSON.

See [Identifiable.scala](src/main/scala/weco/pipeline/id_minter/utils/Identifiable.scala)
for details on the canonical id scheme.

See [IdentifiersDao.scala](src/main/scala/weco/pipeline/id_minter/database/IdentifiersDao.scala)
for details on how the identifiers are stored.

Upstream of here, JSON objects containing "Identifiable" source ids
will have been written to an index. Downstream of here, the source ids
will have been updated with a canonical id.
