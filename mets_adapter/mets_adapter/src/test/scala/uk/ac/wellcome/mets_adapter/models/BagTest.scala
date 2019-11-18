package uk.ac.wellcome.mets_adapter.models

import org.scalatest.{FunSpec, Matchers}

class BagTest extends FunSpec with Matchers {

  describe("METS path") {
    it("parses METS path from Bag") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List(
          "data/b30246039.xml" -> "v1/data/b30246039.xml",
          "data/alto/b30246039_0001.xml" -> "v1/data/alto/b30246039_0001.xml",
          "data/alto/b30246039_0002.xml" -> "v1/data/alto/b30246039_0002.xml",
        )
      )
      bag.metsPath shouldBe Some("digitised/b30246039/v1/data/b30246039.xml")
    }

    it("parses METS path from Bag when not first file") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List(
          "data/alto/b30246039_0001.xml" -> "v1/data/alto/b30246039_0001.xml",
          "data/b30246039.xml" -> "v1/data/b30246039.xml",
          "data/alto/b30246039_0002.xml" -> "v1/data/alto/b30246039_0002.xml",
        )
      )
      bag.metsPath shouldBe Some("digitised/b30246039/v1/data/b30246039.xml")
    }

    it("parses METS path from Bag when b-number ending with x") {
      val bag = createBag(
        s3Path = "digitised/b3024603x",
        files = List("data/b3024603x.xml" -> "v1/data/b3024603x.xml")
      )
      bag.metsPath shouldBe Some("digitised/b3024603x/v1/data/b3024603x.xml")
    }

    it("doesn't parse METS path from Bag when name not prefixed with 'data/'") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List("b30246039.xml" -> "v1/data/b30246039.xml")
      )
      bag.metsPath shouldBe None
    }

    it("doesn't parse METS path from Bag when name isn't XML'") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List("data/b30246039.txt" -> "v1/data/b30246039.xml")
      )
      bag.metsPath shouldBe None
    }
  }

  describe("bag version") {
    it("parses versions from Bag") {
      createBag(version = "v1").parsedVersion shouldBe Some(1)
      createBag(version = "v5").parsedVersion shouldBe Some(5)
      createBag(version = "v060").parsedVersion shouldBe Some(60)
    }

    it("doesn't parse incorrectly formatted versions") {
      createBag(version = "x1").parsedVersion shouldBe None
      createBag(version = "v-1").parsedVersion shouldBe None
      createBag(version = "1").parsedVersion shouldBe None
    }
  }

  describe("METS data") {
    it("extracts all METS data from Bag") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        version = "v2",
        files = List("data/b30246039.xml" -> "v1/data/b30246039.xml"),
      )
      bag.metsData shouldBe Right(MetsData("digitised/b30246039/v1/data/b30246039.xml", 2))
    }

    it("fails extracting METS data if invalid version string") {
      val bag = createBag(
        version = "oops",
        files = List("data/b30246039.xml" -> "v1/data/b30246039.xml"),
      )
      bag.metsData shouldBe a[Left[_, _]]
      bag.metsData.left.get.getMessage shouldBe "Couldn't parse version"
    }

    it("fails extracting METS data if invalid no METS file") {
      val bag = createBag(files = Nil)
      bag.metsData shouldBe a[Left[_, _]]
      bag.metsData.left.get.getMessage shouldBe "Couldn't find METS path"
    }
  }

  def createBag(
    s3Path: String = "path/to/bag",
    version: String = "v1",
    files: List[(String, String)] = Nil) =
    Bag(
      BagInfo("external-identifier"),
      BagManifest(
        files.map { case (name, path) => BagFile(name, path) }
      ),
      BagLocation(
        path = s3Path,
        bucket = "s3-bucket",
      ),
      version,
    )
}
