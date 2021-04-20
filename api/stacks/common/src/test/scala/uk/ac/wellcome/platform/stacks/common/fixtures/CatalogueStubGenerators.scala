package uk.ac.wellcome.platform.stacks.common.fixtures

import uk.ac.wellcome.platform.stacks.common.services.source.CatalogueSource.{
  IdentifiersStub,
  ItemStub,
  WorkStub
}

trait CatalogueStubGenerators extends IdentifierGenerators {
  def createWorkStubWith(items: List[ItemStub]): WorkStub =
    WorkStub(
      id = createStacksWorkIdentifier.value,
      items = items
    )

  def createItemStubWith(identifiers: List[IdentifiersStub]): ItemStub =
    ItemStub(
      id = maybe(createStacksItemIdentifier.value),
      identifiers = Some(identifiers)
    )
}
