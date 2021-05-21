package weco.catalogue.internal_model.locations

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class AccessStatusTest extends AnyFunSpec with Matchers {
  it("sets the name of an AccessStatus") {
    AccessStatus.Open.name shouldBe "Open"
    AccessStatus.Restricted.name shouldBe "Restricted"
  }

  it("creates the Closed AccessStatus") {
    val closedValues = List(
      "Closed",
    )
    closedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Closed)
    }
  }

  it("creates the Restricted AccessStatus") {
    val restrictedValues = List(
      "Restricted",
      "Restricted access (Data Protection Act)",
    )
    restrictedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Restricted)
    }
  }

  it("creates the Unavailable AccessStatus") {
    val unavailableValues = List(
      "Missing.",
      "Deaccessioned"
    )
    unavailableValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Unavailable)
    }
  }

  it("creates the PermissionRequired AccessStatus") {
    val permissionRequiredValues = List(
      "Donor Permission.",
      "Permission is required to view this item.",
    )
    permissionRequiredValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.PermissionRequired)
    }
  }

  it("creates the Open AccessStatus") {
    val openValues = List(
      "Unrestricted",
      "Open",
      "Unrestricted / Open"
    )
    openValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Open)
    }
  }

  it("creates the OpenWithAdvisory AccessStatus") {
    val openWithAdvisoryValues = List(
      "Open with advisory",
    )
    openWithAdvisoryValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.OpenWithAdvisory)
    }
  }

  it("creates the ByAppointment AccessStatus") {
    val byAppointmentValues = List(
      "By Appointment.",
    )
    byAppointmentValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.ByAppointment)
    }
  }

  it("creates the TemporarilyUnavailable AccessStatus") {
    val temporarilyUnavailableValues = List(
      "Temporarily Unavailable.",
    )
    temporarilyUnavailableValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(
        AccessStatus.TemporarilyUnavailable)
    }
  }

  it("strips punctuation from the AccessStatus") {
    AccessStatus.apply("Open.") shouldBe Right(AccessStatus.Open)
  }

  it("errors if invalid AccessStatus") {
    val result = AccessStatus.apply("Oopsy")
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[UnknownAccessStatus]
    result.left.get.getMessage shouldBe "Oopsy"
  }
}
