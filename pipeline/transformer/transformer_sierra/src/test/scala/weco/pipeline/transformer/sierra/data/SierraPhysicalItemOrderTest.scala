package weco.pipeline.transformer.sierra.data

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.internal_model.work.generators.ItemsGenerators
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.sierra.models.identifiers.{SierraBibNumber, SierraItemNumber}

class SierraPhysicalItemOrderTest
    extends AnyFunSpec
    with Matchers
    with ItemsGenerators
    with SierraRecordGenerators {
  it("uses alphabetical ordering of IDs if there's no explicit override") {
    val items = List(
      createItem(id = createSierraItemNumber),
      createItem(id = createSierraItemNumber),
      createItem(id = createSierraItemNumber)
    )

    SierraPhysicalItemOrder(SierraBibNumber("1000000"), items) shouldBe items
      .sortBy { _.id.sourceIdentifier.value }
  }

  it("reorders items if there's an explicit override") {
    val item1 = createItem(id = SierraItemNumber("1874354"))
    val item2 = createItem(id = SierraItemNumber("1874355"))
    val item3 = createItem(id = SierraItemNumber("1000031"))
    val item4 = createItem(id = SierraItemNumber("1874353"))

    val items = List(item1, item2, item3, item4)
      .sortBy {
        it =>
          it.id.sourceIdentifier.value
      }

    SierraPhysicalItemOrder(SierraBibNumber("1000024"), items) shouldBe List(
      item1,
      item2,
      item3,
      item4
    )
  }

  it("puts any items not mentioned in the override at the end]") {
    val item1 = createItem(id = SierraItemNumber("1422975"))
    val item2 = createItem(id = SierraItemNumber("1000363"))
    val item3 = createItem(id = SierraItemNumber("2222222"))
    val item4 = createItem(id = SierraItemNumber("1111111"))

    val items = List(item1, item2, item3, item4)

    SierraPhysicalItemOrder(SierraBibNumber("1000309"), items) shouldBe List(
      item1,
      item2,
      item4,
      item3
    )
  }

  def createItem(id: SierraItemNumber): Item[IdState.Identifiable] =
    Item(
      id = IdState.Identifiable(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          value = id.withCheckDigit,
          ontologyType = "Item"
        ),
        otherIdentifiers = List(
          SourceIdentifier(
            identifierType = IdentifierType.SierraIdentifier,
            value = id.withoutCheckDigit,
            ontologyType = "Item"
          )
        )
      ),
      locations = List(createPhysicalLocation)
    )
}
