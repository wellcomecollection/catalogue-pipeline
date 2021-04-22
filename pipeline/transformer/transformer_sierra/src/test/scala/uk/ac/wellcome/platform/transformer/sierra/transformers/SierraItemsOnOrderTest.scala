package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.{
  FixedField,
  SierraItemData,
  SierraOrderData
}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.identifiers.IdState.Unidentifiable
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocation}
import weco.catalogue.internal_model.work.Item

class SierraItemsOnOrderTest extends AnyFunSpec with Matchers with SierraDataGenerators {
  it("returns nothing if there are no orders or items") {
    getOrders(itemData = List(), orderData = List()) shouldBe empty
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "1 copy ordered for Wellcome Collection on 1 January 2001"
            )
          )
        ),
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "2 copies ordered for Wellcome Collection on 2 February 2002"
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "10 copies ordered for Wellcome Collection"
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "3 copies ordered for Wellcome Collection"
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

      getOrders(itemData = List(), orderData = orderData) should not be empty

      val suppressedOrderData = orderData.map { od => od.copy(suppressed = true) }
      getOrders(itemData = List(), orderData = suppressedOrderData) shouldBe empty
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

      getOrders(itemData = List(), orderData = orderData) should not be empty

      val deletedOrderData = orderData.map { od => od.copy(deleted = true) }
      getOrders(itemData = List(), orderData = deletedOrderData) shouldBe empty
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
      getOrders(itemData = List(), orderData = orderData) should not be empty
      getOrders(itemData = List(createSierraItemData), orderData = orderData) shouldBe empty
    }
  }

  describe("returns 'awaiting cataloguing' items") {
    it("if there are orders with status 'a'") {
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "1 copy awaiting cataloguing for Wellcome Collection"
            )
          )
        ),
        Item(
          id = Unidentifiable,
          title = None,
          locations = List(
            PhysicalLocation(
              locationType = LocationType.OnOrder,
              label = "2 copies awaiting cataloguing for Wellcome Collection"
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
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

      getOrders(itemData = List(), orderData = orderData) shouldBe List(
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

    it("unless the order has no RDATE") {
      val orderData = List(
        createSierraOrderDataWith(
          fixedFields = Map(
            "5" -> FixedField(label = "COPIES", value = "1"),
            "20" -> FixedField(label = "STATUS", value = "a")
          )
        )
      )

      getOrders(itemData = List(), orderData = orderData) shouldBe empty

      val orderWithRdate = orderData.map { od => od.copy(fixedFields = od.fixedFields ++ Map("17" -> FixedField(label = "RDATE", value = "2008-08-08"))) }
      getOrders(itemData = List(), orderData = orderWithRdate) should not be empty
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

      getOrders(itemData = List(), orderData = orderData) should not be empty

      val suppressedOrderData = orderData.map { od => od.copy(suppressed = true) }
      getOrders(itemData = List(), orderData = suppressedOrderData) shouldBe empty
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

      getOrders(itemData = List(), orderData = orderData) should not be empty

      val deletedOrderData = orderData.map { od => od.copy(deleted = true) }
      getOrders(itemData = List(), orderData = deletedOrderData) shouldBe empty
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

      // Note: we test both with and without order data here, so we'll
      // spot if the lack of output is unrelated to the items.
      getOrders(itemData = List(), orderData = orderData) should not be empty
      getOrders(itemData = List(createSierraItemData), orderData = orderData) shouldBe empty
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

      getOrders(itemData = List(), orderData = orderData) shouldBe empty
    }
  }

  def getOrders(itemData: List[SierraItemData], orderData: List[SierraOrderData]): List[Item[IdState.Unidentifiable.type]] = {
    val id = createSierraBibNumber

    val itemIds = (1 to itemData.size)
      .map { _ => createSierraItemNumber }
      .sortBy { _.withoutCheckDigit }

    val orderIds = (1 to orderData.size)
      .map { _ => createSierraOrderNumber }
      .sortBy { _.withoutCheckDigit }

    val itemDataMap = itemIds.zip(itemData).toMap
    val orderDataMap = orderIds.zip(orderData).toMap

    SierraItemsOnOrder(id, itemDataMap, orderDataMap)
  }
}
