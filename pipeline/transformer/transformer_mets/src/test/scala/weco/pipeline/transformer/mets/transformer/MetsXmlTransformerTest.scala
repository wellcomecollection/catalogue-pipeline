package weco.pipeline.transformer.mets.transformer

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{AccessStatus, License}
import weco.catalogue.source_model.mets.{DeletedMetsFile, MetsFileWithImages}
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.GoobiMetsGenerators
import weco.pipeline.transformer.mets.transformer.models.FileReference
import weco.pipeline.transformer.mets.transformers.MetsAccessConditions
import weco.pipeline.transformer.result.Result
import weco.sierra.generators.SierraIdentifierGenerators
import weco.storage.providers.s3.{S3ObjectLocation, S3ObjectLocationPrefix}
import weco.storage.store.memory.MemoryStore

import java.time.Instant

class MetsXmlTransformerTest
    extends AnyFunSpec
    with Matchers
    with GoobiMetsGenerators
    with SierraIdentifierGenerators
    with LocalResources {

  it("transforms METS XML") {
    val xml = readResource("b30246039.xml")
    val fileReferences = createFileReferences(6, "b30246039")
    val thumbnailRef = fileReferences(5)
    val now = Instant.now
    transform(root = Some(xml), modifiedTime = now) shouldBe Right(
      InvisibleMetsData(
        recordIdentifier = "b30246039",
        title = "[Report 1942] /",
        accessConditions = MetsAccessConditions(
          licence = Some(License.CCBYNC),
          accessStatus = Some(AccessStatus.Open),
          usage = Some("Some terms")
        ),
        fileReferences = fileReferences,
        thumbnailReference = Some(thumbnailRef),
        version = 1,
        modifiedTime = now,
        createdDate = Some("2018-06-26T23:45:51Z"),
        locationPrefix = "v2/"
      )
    )
  }

  it("returns empty MetsData if the MetsLocation is marked as deleted") {
    val str = goobiMetsXmlWith(
      recordIdentifier = "b30246039",
      accessConditionStatus = Some("Open"),
      license = Some(License.CC0)
    )
    val now = Instant.now
    transform(
      id = "b30246039",
      root = Some(str),
      modifiedTime = now,
      deleted = true
    ) shouldBe Right(DeletedMetsData("b30246039", 1, now))
  }

  it("errors when the root XML doesn't exist in the store") {
    transform(root = None, modifiedTime = Instant.now) shouldBe a[Left[_, _]]
  }

  it("transforms METS XML with manifestations") {
    val xml = readResource("b22012692.xml")
    val manifestations = Map(
      "b22012692_0003.xml" -> Some(readResource("b22012692_0003.xml")),
      "b22012692_0001.xml" -> Some(readResource("b22012692_0001.xml"))
    )
    val fileReferences = createFileReferences(2, "b22012692", Some(1))
    val thumbnailRef = fileReferences.head
    val now = Instant.now
    transform(
      root = Some(xml),
      modifiedTime = now,
      manifestations = manifestations
    ) shouldBe Right(
      InvisibleMetsData(
        recordIdentifier = "b22012692",
        title =
          "Enciclopedia anatomica che comprende l'anatomia descrittiva, l'anatomia generale, l'anatomia patologica, la storia dello sviluppo e delle razze umane /",
        MetsAccessConditions(
          licence = Some(License.PDM),
          accessStatus = Some(AccessStatus.Open)
        ),
        fileReferences = fileReferences,
        thumbnailReference = Some(thumbnailRef),
        version = 1,
        modifiedTime = now,
        createdDate = Some("2016-09-07T09:38:57"),
        locationPrefix = "v2/"
      )
    )
  }

  it("transforms METS XML with manifestations without .xml in the name") {
    val title = "[Report 1942] /"

    val xml = xmlWithManifestations(
      title = title,
      manifestations =
        List(("LOG_0001", "01", "first"), ("LOG_0002", "02", "second.xml"))
    ).toString()

    val manifestations = Map(
      "first.xml" -> Some(
        goobiMetsXmlWith(
          recordIdentifier = "b30246039",
          title = title,
          license = Some(License.InCopyright),
          fileSec = fileSec("b30246039"),
          structMap = structMap
        )
      ),
      "second.xml" -> Some(
        goobiMetsXmlWith(recordIdentifier = "b30246039", title = title)
      )
    )
    val fileReferences = createFileReferences(2, "b30246039")
    val thumbnailRef = fileReferences.head
    val now = Instant.now
    transform(
      root = Some(xml),
      modifiedTime = now,
      manifestations = manifestations
    ) shouldBe Right(
      InvisibleMetsData(
        recordIdentifier = "b30246039",
        title = title,
        accessConditions =
          MetsAccessConditions(licence = Some(License.InCopyright)),
        version = 1,
        modifiedTime = now,
        createdDate = Some("2016-09-07T09:38:57"),
        locationPrefix = "v2/",
        fileReferences = createFileReferences(2, "b30246039"),
        thumbnailReference = Some(thumbnailRef)
      )
    )
  }

  it("ignores CREATEDATE for Goobi METS when version is not 1") {
    val xml = readResource("b30246039.xml")
    val fileReferences = createFileReferences(6, "b30246039")
    val thumbnailRef = fileReferences(5)
    val now = Instant.now
    transform(root = Some(xml), modifiedTime = now, version = 2) shouldBe Right(
      InvisibleMetsData(
        recordIdentifier = "b30246039",
        title = "[Report 1942] /",
        accessConditions = MetsAccessConditions(
          licence = Some(License.CCBYNC),
          accessStatus = Some(AccessStatus.Open),
          usage = Some("Some terms")
        ),
        fileReferences = fileReferences,
        thumbnailReference = Some(thumbnailRef),
        version = 2,
        modifiedTime = now,
        createdDate = None, // Should be None for version != 1
        locationPrefix = "v2/"
      )
    )
  }

  // TODO, I'm not sure this should error, it should warn and best-guess.
  it("errors if first manifestation doesn't exist in store") {
    val xml = readResource("b22012692.xml")
    val manifestations = Map(
      "b22012692_0003.xml" -> Some(readResource("b22012692_0003.xml")),
      "b22012692_0001.xml" -> None
    )
    transform(
      root = Some(xml),
      modifiedTime = Instant.now,
      manifestations = manifestations
    ) shouldBe a[Left[_, _]]
  }

  def transform(
    id: String = createSierraBibNumber.withoutCheckDigit,
    root: Option[String],
    modifiedTime: Instant,
    deleted: Boolean = false,
    manifestations: Map[String, Option[String]] = Map.empty,
    version: Int = 1
  ): Result[MetsData] = {

    val metsSourceData = if (deleted) {
      DeletedMetsFile(
        modifiedTime = modifiedTime,
        version = version
      )
    } else {
      MetsFileWithImages(
        root = S3ObjectLocationPrefix(bucket = "bucket", keyPrefix = "path"),
        version = version,
        filename = if (root.nonEmpty) "root.xml" else "nonexistent.xml",
        modifiedTime = modifiedTime,
        manifestations = manifestations.toList.map { case (file, _) => file }
      )
    }

    val store = new MemoryStore(
      (manifestations ++ root
        .map(content => "root.xml" -> Some(content))).collect {
        case (file, Some(content)) =>
          S3ObjectLocation("bucket", key = s"path/$file") -> content
      }
    )

    new MetsXmlTransformer(store).transform(id, metsSourceData)
  }

  def createFileReferences(
    n: Int,
    bumber: String,
    manifestN: Option[Int] = None
  ): List[FileReference] =
    (1 to n).toList.map {
      i =>
        FileReference(
          f"FILE_$i%04d_OBJECTS",
          manifestN match {
            case None    => f"objects/$bumber%s_$i%04d.jp2"
            case Some(n) => f"objects/$bumber%s_$n%04d_$i%04d.jp2"
          },
          Some("image/jp2")
        )
    }

  def createIds(n: Int): List[String] =
    (1 to n).map {
      idx =>
        f"FILE_$idx%04d_OBJECTS"
    }.toList
}
