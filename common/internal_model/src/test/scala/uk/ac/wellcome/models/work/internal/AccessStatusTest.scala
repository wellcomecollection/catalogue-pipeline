package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class AccessStatusTest extends AnyFunSpec with Matchers {
  it("creates the Closed access status") {
    val closedValues = List(
      "Closed",
      "The file is closed until 2059",
      "This file is closed until 2060",
      "The papers are closed until some future date"
    )
    closedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Closed)
    }
  }

  it("creates restricted access condition") {
    val restrictedValues = List(
      "Restricted",
      "Restricted access (Data Protection Act)",
      "Cannot Be Produced - View Digitised Version",
      "Certain restrictions apply.",
      "By Appointment.",
      "Restricted: currently undergoing conservation.",
      "The file is restricted for data protection reasons",
      "This file is restricted for sensitivity reasons"
    )
    restrictedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Restricted)
    }
  }

  it("creates Unavailable access condition") {
    val restrictedValues = List("Missing.", "Temporarily Unavailable.")
    restrictedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Unavailable)
    }
  }
  it("creates PermissionRequired access condition") {
    val restrictedValues = List(
      "Permission Required.",
      "Donor Permission.",
      "Permission is required to view this item.",
      "Permission must be obtained from the Winnicott Trust before access can be granted"
    )
    restrictedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.PermissionRequired)
    }
  }

  it("creates the Open access condition") {
    val openValues = List(
      "Unrestricted",
      "Open",
      "Unrestricted / Open"
    )
    openValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Open)
    }
  }

  it("strips punctuation from access condition if present") {
    AccessStatus.apply("Open.") shouldBe Right(AccessStatus.Open)
  }

  it("errors if invalid AccessStatus") {
    val result = AccessStatus.apply("Oopsy")
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[UnknownAccessStatus]
    result.left.get.getMessage shouldBe "Oopsy"
  }
}
