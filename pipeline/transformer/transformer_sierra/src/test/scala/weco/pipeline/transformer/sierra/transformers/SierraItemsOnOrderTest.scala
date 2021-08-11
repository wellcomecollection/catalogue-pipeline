package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.identifiers.IdState.Unidentifiable
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocation}
import weco.catalogue.internal_model.work.Item
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.{SierraBibData, SierraOrderData}
import weco.sierra.models.marc.FixedField

class SierraItemsOnOrderTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  it("returns nothing if there are no orders or items") {
    getOrders(hasItems = false, orderData = List()) shouldBe empty
  }

  describe("creates 'on order' items") {
    it("if there are orders with status 'o' and no RDATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 1 January 2001"
            )
          )
        ),
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 2 February 2002"
            )
          )
        )
      )
    }

    it("if there are orders with status 'a' and no RDATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 1 January 2001"
            )
          )
        ),
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 2 February 2002"
            )
          )
        )
      )
    }

    it("if there are orders with status 'c' and no RDATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "c")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 1 January 2001"
            )
          )
        )
      )
    }

    it("deduplicates the items it creates") {
      // Because we don't expose the number of copies in the API, duplicate
      // items are less useful.
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 1 January 2001"
            )
          )
        )
      )
    }

    it("if the number of copies is missing") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "13" -> FixedField(label = "ODATE", value = "2003-03-03"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 3 March 2003"
            )
          )
        )
      )
    }

    it("if the number of copies is not an integer") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "NaN"),
            "13" -> FixedField(label = "ODATE", value = "2004-04-04"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection on 4 April 2004"
            )
          )
        )
      )
    }

    it("if the order date is missing") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "10"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection"
            )
          )
        )
      )
    }

    it("if the order date is unparseable") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "3"),
            "13" -> FixedField(label = "ODATE", value = "tomorrow"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Ordered for Wellcome Collection"
            )
          )
        )
      )
    }

    it("unless the order is suppressed") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) should not be empty

      val suppressedOrderData = orderData.map { od =>
        od.copy(suppressed = true)
      }
      getOrders(hasItems = false, orderData = suppressedOrderData) shouldBe empty
    }

    it("unless the order is deleted") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) should not be empty

      val deletedOrderData = orderData.map { od =>
        od.copy(deleted = true)
      }
      getOrders(hasItems = false, orderData = deletedOrderData) shouldBe empty
    }

    it("unless there are any items") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      // Note: we test both with and without order data here, so we'll
      // spot if the lack of output is unrelated to the items.
      getOrders(hasItems = false, orderData = orderData) should not be empty
      getOrders(hasItems = true, orderData = orderData) shouldBe empty
    }

    it("unless there is a CAT DATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "13" -> FixedField(label = "ODATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "o")
          )
        )
      )

      val bibData = createSierraBibData
      val bibDataWithCatDate =
        bibData.copy(
          fixedFields =
            Map("28" -> FixedField(label = "CAT DATE", value = "2021-05-17"))
        )

      // Note: we test both with and without CAT DATE here, so we'll
      // spot if the lack of output is unrelated to the items.
      getOrders(bibData = bibData, orderData = orderData) should not be empty
      getOrders(bibData = bibDataWithCatDate, orderData = orderData) shouldBe empty
    }
  }

  describe("returns 'awaiting cataloguing' items") {
    it("if there are orders with status 'a' and an RDATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "17" -> FixedField(label = "RDATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Awaiting cataloguing for Wellcome Collection"
            )
          )
        )
      )
    }

    it("if the number of copies is omitted") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Awaiting cataloguing for Wellcome Collection"
            )
          )
        )
      )
    }

    it("if the number of copies is not an integer") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "NaN"),
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "Awaiting cataloguing for Wellcome Collection"
            )
          )
        )
      )
    }

    it("unless the order is suppressed") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) should not be empty

      val suppressedOrderData = orderData.map { od =>
        od.copy(suppressed = true)
      }
      getOrders(hasItems = false, orderData = suppressedOrderData) shouldBe empty
    }

    it("unless the order is deleted") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) should not be empty

      val deletedOrderData = orderData.map { od =>
        od.copy(deleted = true)
      }
      getOrders(hasItems = false, orderData = deletedOrderData) shouldBe empty
    }

    it("unless there are any items") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      // Note: we test both with and without items here, so we'll
      // spot if the lack of output is unrelated to the items.
      getOrders(hasItems = false, orderData = orderData) should not be empty
      getOrders(hasItems = true, orderData = orderData) shouldBe empty
    }

    it("unless there is a CAT DATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "17" -> FixedField(label = "RDATE", value = "2001-01-01"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      val bibData = createSierraBibData
      val bibDataWithCatDate =
        bibData.copy(
          fixedFields =
            Map("28" -> FixedField(label = "CAT DATE", value = "2021-05-17"))
        )

      // Note: we test both with and without CAT DATE here, so we'll
      // spot if the lack of output is unrelated to the items.
      getOrders(bibData = bibData, orderData = orderData) should not be empty
      getOrders(bibData = bibDataWithCatDate, orderData = orderData) shouldBe empty
    }
  }

  describe("skips unrecognised order records") {
    it("if they have an unrecognised status") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "20" -> FixedField(label = "STATUS", value = "x")
          )
        ),
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "2"),
            "13" -> FixedField(label = "ODATE", value = "2002-02-02"),
            "20" -> FixedField(label = "STATUS", value = "z")
          )
        )
      )

      getOrders(hasItems = false, orderData = orderData) shouldBe empty
    }
  }

  def getOrders(hasItems: Boolean = false,
                bibData: SierraBibData = createSierraBibData,
                orderData: List[SierraOrderData])
    : List[Item[IdState.Unidentifiable.type]] = {
    val id = createSierraBibNumber

    val orderIds = (1 to orderData.size)
      .map { _ =>
        createSierraOrderNumber
      }
      .sortBy { _.withoutCheckDigit }

    val orderDataMap = orderIds.zip(orderData).toMap

    SierraItemsOnOrder(id, bibData, hasItems, orderDataMap)
  }
}
