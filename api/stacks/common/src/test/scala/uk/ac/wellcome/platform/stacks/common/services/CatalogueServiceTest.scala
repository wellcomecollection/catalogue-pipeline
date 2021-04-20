package uk.ac.wellcome.platform.stacks.common.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.stacks.common.fixtures.CatalogueStubGenerators
import uk.ac.wellcome.platform.stacks.common.models._
import uk.ac.wellcome.platform.stacks.common.services.source.CatalogueSource
import uk.ac.wellcome.platform.stacks.common.services.source.CatalogueSource._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CatalogueServiceTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with CatalogueStubGenerators {

  describe("getAllStacksItems") {
    class OneWorkCatalogueSource(work: WorkStub) extends CatalogueSource {
      override def getWorkStub(id: StacksWorkIdentifier): Future[WorkStub] =
        Future.successful(work)

      override def getSearchStub(
        identifier: Identifier[_]
      ): Future[SearchStub] =
        Future.failed(new Throwable("BOOM!"))
    }

    it("finds an item on a work") {
      val item = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1234567")))
      )

      val work = createWorkStubWith(
        items = List(item)
      )

      val catalogueSource = new OneWorkCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getAllStacksItems(createStacksWorkIdentifier)) {
        _ shouldBe List(
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item.id.get),
            sierraId = SierraItemIdentifier(1234567)
          )
        )
      }
    }

    it("finds multiple matching items on a work") {
      val item1 = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1234567")))
      )

      val item2 = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1111111")))
      )

      val work = createWorkStubWith(
        items = List(item1, item2)
      )

      val catalogueSource = new OneWorkCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getAllStacksItems(createStacksWorkIdentifier)) {
        _ shouldBe List(
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item1.id.get),
            sierraId = SierraItemIdentifier(1234567)
          ),
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item2.id.get),
            sierraId = SierraItemIdentifier(1111111)
          )
        )
      }
    }

    it("ignores items that don't have a sierra-identifier") {
      val item = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(
          List(
            IdentifiersStub(
              identifierType =
                TypeStub(id = "miro-image-number", label = "Miro image number"),
              value = "A0001234"
            )
          )
        )
      )

      val work = createWorkStubWith(
        items = List(item)
      )

      val catalogueSource = new OneWorkCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getAllStacksItems(createStacksWorkIdentifier)) {
        _ shouldBe empty
      }
    }

    it("throws an error if an item has multiple sierra-identifier values") {
      val item = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(
          List(
            createSierraIdentifier("1234567"),
            createSierraIdentifier("1111111")
          )
        )
      )

      val work = createWorkStubWith(
        items = List(item)
      )

      val catalogueSource = new OneWorkCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getAllStacksItems(createStacksWorkIdentifier).failed) {
        err =>
          err shouldBe a[Exception]
          err.getMessage should startWith(
            "Multiple values for sierra-identifier"
          )
      }
    }

    it("throws an error if it cannot parse the Sierra identifier as a Long") {
      val item = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("Not a Number")))
      )

      val work = createWorkStubWith(
        items = List(item)
      )

      val catalogueSource = new OneWorkCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getAllStacksItems(createStacksWorkIdentifier).failed) {
        err =>
          err shouldBe a[Exception]
          err.getMessage shouldBe "Unable to convert Not a Number to Long!"
      }
    }
  }

  describe("getStacksItem") {
    class MockCatalogueSource(works: WorkStub*) extends CatalogueSource {
      override def getWorkStub(id: StacksWorkIdentifier): Future[WorkStub] =
        Future.failed(new Throwable("BOOM!"))

      override def getSearchStub(
        identifier: Identifier[_]
      ): Future[SearchStub] =
        Future.successful(
          SearchStub(totalResults = works.size, results = works.toList)
        )
    }

    it("returns an empty list if there are no matching works") {
      val catalogueSource = new MockCatalogueSource()
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getStacksItem(createStacksItemIdentifier)) {
        _ shouldBe None
      }
    }

    it("gets an item from a work") {
      val item = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1234567")))
      )

      val work = createWorkStubWith(items = List(item))

      val catalogueSource = new MockCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getStacksItem(CatalogueItemIdentifier(item.id.get))) {
        _ shouldBe Some(
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item.id.get),
            sierraId = SierraItemIdentifier(1234567)
          )
        )
      }
    }

    it("filters results by catalogue item identifier") {
      val item1 = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1111111")))
      )

      val work1 = createWorkStubWith(items = List(item1))

      val item2 = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("2222222")))
      )

      val work2 = createWorkStubWith(items = List(item2))

      val catalogueSource = new MockCatalogueSource(work1, work2)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getStacksItem(CatalogueItemIdentifier(item1.id.get))) {
        _ shouldBe Some(
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item1.id.get),
            sierraId = SierraItemIdentifier(1111111)
          )
        )
      }
    }

    it("filters results by Sierra item identifier") {
      val item1 = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1111111")))
      )

      val work1 = createWorkStubWith(items = List(item1))

      val item2 = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("2222222")))
      )

      val work2 = createWorkStubWith(items = List(item2))

      val catalogueSource = new MockCatalogueSource(work1, work2)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getStacksItem(SierraItemIdentifier(1111111))) {
        _ shouldBe Some(
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item1.id.get),
            sierraId = SierraItemIdentifier(1111111)
          )
        )
      }
    }

    it("de-duplicates results if the same item appears on multiple works") {
      val item = ItemStub(
        id = Some(createStacksItemIdentifier.value),
        identifiers = Some(List(createSierraIdentifier("1111111")))
      )

      val works = (1 to 5).map { _ =>
        createWorkStubWith(items = List(item))
      }

      val catalogueSource = new MockCatalogueSource(works: _*)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getStacksItem(SierraItemIdentifier(1111111))) {
        _ shouldBe Some(
          StacksItemIdentifier(
            catalogueId = CatalogueItemIdentifier(item.id.get),
            sierraId = SierraItemIdentifier(1111111)
          )
        )
      }
    }

    it("errors if it finds multiple matching items") {
      val catalogueId = createStacksItemIdentifier

      val item1 = ItemStub(
        id = Some(catalogueId.value),
        identifiers = Some(List(createSierraIdentifier("1111111")))
      )

      val item2 = ItemStub(
        id = Some(catalogueId.value),
        identifiers = Some(List(createSierraIdentifier("22222222")))
      )

      val work = createWorkStubWith(items = List(item1, item2))

      val catalogueSource = new MockCatalogueSource(work)
      val service = new CatalogueService(catalogueSource)

      whenReady(service.getStacksItem(catalogueId).failed) { err =>
        err shouldBe a[Exception]
        err.getMessage should startWith("Found multiple matching items for")
      }
    }
  }

  describe("handling errors from CatalogueSource") {
    val getException = new Throwable("BOOM!")
    val searchException = new Throwable("BANG!")

    class BrokenCatalogueSource extends CatalogueSource {
      override def getWorkStub(id: StacksWorkIdentifier): Future[WorkStub] =
        Future.failed(getException)

      override def getSearchStub(
        identifier: Identifier[_]
      ): Future[SearchStub] =
        Future.failed(searchException)
    }

    val catalogueSource = new BrokenCatalogueSource()
    val service = new CatalogueService(catalogueSource)

    it("inside getAllStacksItems") {
      whenReady(service.getAllStacksItems(createStacksWorkIdentifier).failed) {
        _ shouldBe getException
      }
    }

    it("inside getStacksItem") {
      whenReady(service.getStacksItem(createStacksItemIdentifier).failed) {
        _ shouldBe searchException
      }
    }
  }

  private def createSierraIdentifier(value: String): IdentifiersStub =
    IdentifiersStub(
      identifierType =
        TypeStub(id = "sierra-identifier", label = "Sierra identifier"),
      value = value
    )
}
