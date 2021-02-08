package uk.ac.wellcome.models.work.internal

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
      "The file is closed until 2059",
      "This file is closed until 2060",
      "The papers are closed until some future date"
    )
    closedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Closed)
    }
  }

  it("creates the Restricted AccessStatus") {
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

  it("creates the Unavailable AccessStatus") {
    val restrictedValues = List("Missing.", "Temporarily Unavailable.")
    restrictedValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.Unavailable)
    }
  }

  it("creates the PermissionRequired AccessStatus") {
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
    val openValues = List(
      "Open with advisory",
      "Requires registration to view"
    )
    openValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.OpenWithAdvisory)
    }
  }

  it("creates the LicensedResources AccessStatus") {
    val openValues = List(
      "In copyright to the Earl of Ownership"
    )
    openValues.foreach { str =>
      AccessStatus.apply(str) shouldBe Right(AccessStatus.LicensedResources)
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
