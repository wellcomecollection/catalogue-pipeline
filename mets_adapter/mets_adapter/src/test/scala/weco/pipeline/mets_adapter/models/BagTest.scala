package weco.pipeline.mets_adapter.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.storage.providers.s3.S3ObjectLocationPrefix

import java.time.Instant
import weco.catalogue.source_model.mets.{DeletedMetsFile, MetsFileWithImages}

class BagTest extends AnyFunSpec with Matchers {

  describe("METS path") {
    it("parses METS file from Bag") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List(
          "data/b30246039.xml" -> "v1/data/b30246039.xml",
          "data/alto/b30246039_0001.xml" -> "v1/data/alto/b30246039_0001.xml",
          "data/alto/b30246039_0002.xml" -> "v1/data/alto/b30246039_0002.xml"
        )
      )
      bag.metsFile shouldBe Right("v1/data/b30246039.xml")
    }

    it("parses METS file from Bag when not first file") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List(
          "data/alto/b30246039_0001.xml" -> "v1/data/alto/b30246039_0001.xml",
          "data/b30246039.xml" -> "v1/data/b30246039.xml",
          "data/alto/b30246039_0002.xml" -> "v1/data/alto/b30246039_0002.xml"
        )
      )
      bag.metsFile shouldBe Right("v1/data/b30246039.xml")
    }

    it("parses METS file from Bag when b-number ending with x") {
      val bag = createBag(
        s3Path = "digitised/b3024603x",
        files = List("data/b3024603x.xml" -> "v1/data/b3024603x.xml")
      )
      bag.metsFile shouldBe Right("v1/data/b3024603x.xml")
    }

    it("parses METS file from Bag when in born-digital METS...xml form") {
      val bag = createBag(
        s3Path = "born-digital/ARTCOO/A/3/1",
        files = List(
          "data/METS.2651ee7c-d56d-4699-a194-6a5a2652a596.xml" -> "v1/data/METS.2651ee7c-d56d-4699-a194-6a5a2652a596.xml"
        )
      )
      bag.metsFile shouldBe Right(
        "v1/data/METS.2651ee7c-d56d-4699-a194-6a5a2652a596.xml"
      )
    }

    it("doesn't parse METS file from Bag when name not prefixed with 'data/'") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List("b30246039.xml" -> "v1/data/b30246039.xml")
      )
      bag.metsFile shouldBe a[Left[_, _]]
    }

    it("doesn't parse METS file from Bag when name isn't XML'") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List("data/b30246039.txt" -> "v1/data/b30246039.xml")
      )
      bag.metsFile shouldBe a[Left[_, _]]
    }
  }

  describe("bag version") {
    it("parses versions from Bag") {
      createBag(version = "v1").parsedVersion shouldBe Right(1)
      createBag(version = "v5").parsedVersion shouldBe Right(5)
      createBag(version = "v060").parsedVersion shouldBe Right(60)
    }

    it("doesn't parse incorrectly formatted versions") {
      createBag(version = "x1").parsedVersion shouldBe a[Left[_, _]]
      createBag(version = "v-1").parsedVersion shouldBe a[Left[_, _]]
      createBag(version = "1").parsedVersion shouldBe a[Left[_, _]]
    }
  }

  describe("METS manifestations") {
    it("parses a list of manifestations") {
      val bag = createBag(
        s3Path = "digitised/b30246039",
        files = List(
          "data/b30246039.txt" -> "v1/data/b30246039.xml",
          "data/alto/b30246039_0001.xml" -> "v1/data/alto/b30246039_0001.xml",
          "data/alto/b30246039_0002.xml" -> "v1/data/alto/b30246039_0002.xml",
          "data/b30246039_0001.xml" -> "v1/data/b30246039_0001.xml",
          "data/b30246039_0002.xml" -> "v1/data/b30246039_0002.xml"
        )
      )
      bag.manifestations shouldBe List(
        "v1/data/b30246039_0001.xml",
        "v1/data/b30246039_0002.xml"
      )
    }
  }

  describe("METS data") {
    it("extracts all METS data from Bag") {
      val bag = createBag(
        bucket = "wellcomecollection-example-storage",
        s3Path = "digitised/b1234",
        version = "v2",
        files = List(
          "data/b30246039.xml" -> "v1/data/b30246039.xml",
          "objects/blahbluh.jp2" -> "v1/objects/blahbluh.jp2"
        )
      )
      bag.metsSourceData shouldBe Right(
        MetsFileWithImages(
          root = S3ObjectLocationPrefix(
            bucket = "wellcomecollection-example-storage",
            keyPrefix = "digitised/b1234"
          ),
          filename = "v1/data/b30246039.xml",
          manifestations = List.empty,
          modifiedTime = bag.createdDate,
          version = 2
        )
      )
    }

    it(
      "marks a METS data as deleted if there are no other assets except METS"
    ) {
      val bag = createBag(
        version = "v2",
        files = List("data/b30246039.xml" -> "v1/data/b30246039.xml")
      )
      bag.metsSourceData shouldBe Right(
        DeletedMetsFile(
          modifiedTime = bag.createdDate,
          version = 2
        )
      )
    }

    it("marks a METS data as deleted if the bag manifest is empty") {
      val bag = createBag(
        version = "v3",
        files = List()
      )
      bag.metsSourceData shouldBe Right(
        DeletedMetsFile(
          modifiedTime = bag.createdDate,
          version = 3
        )
      )
    }

    it("fails extracting METS data if the version string is invalid") {
      val bag = createBag(
        version = "oops",
        files = List("data/b30246039.xml" -> "v1/data/b30246039.xml")
      )
      bag.metsSourceData shouldBe a[Left[_, _]]
      bag.metsSourceData.left.get.getMessage shouldBe "Couldn't parse version"
    }
  }

  def createBag(
    s3Path: String = "digitised/b30246039",
    bucket: String = "bucket",
    version: String = "v1",
    files: List[(String, String)] = List(
      "data/b30246039.xml" -> "v1/data/b30246039.xml"
    )
  ) =
    Bag(
      BagInfo("external-identifier"),
      BagManifest(
        files.map { case (name, path) => BagFile(name, path) }
      ),
      BagLocation(
        path = s3Path,
        bucket = bucket
      ),
      version,
      createdDate = Instant.now
    )
}
