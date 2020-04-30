package uk.ac.wellcome.platform.transformer.mets.transformer

import org.scalatest.matchers.should.Matchers
import org.apache.commons.io.IOUtils
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.models.work.internal.License
import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators
import uk.ac.wellcome.storage.store.memory.MemoryStore
import uk.ac.wellcome.storage.ObjectLocation

class MetsXmlTransformerTest extends AnyFunSpec with Matchers with MetsGenerators {

  it("should transform METS XML") {
    val xml = loadXmlFile("/b30246039.xml")
    transform(Some(xml)) shouldBe Right(
      MetsData(
        recordIdentifier = "b30246039",
        accessConditionDz = Some("CC-BY-NC"),
        accessConditionStatus = Some("Open"),
        accessConditionUsage = Some("Some terms"),
        fileReferences = createFileReferences(6, "b30246039")
      )
    )
  }

  it("should error when the root XML doesn't exist in the store") {
    transform(None) shouldBe a[Left[_, _]]
  }

  it("should transform METS XML with manifestations") {
    val xml = loadXmlFile("/b22012692.xml")
    val manifestations = Map(
      "b22012692_0003.xml" -> Some(loadXmlFile("/b22012692_0003.xml")),
      "b22012692_0001.xml" -> Some(loadXmlFile("/b22012692_0001.xml")),
    )
    transform(Some(xml), manifestations) shouldBe Right(
      MetsData(
        recordIdentifier = "b22012692",
        accessConditionDz = Some("PDM"),
        accessConditionStatus = Some("Open"),
        fileReferences = createFileReferences(2, "b22012692", Some(1))
      )
    )
  }

  it("should transform METS XML with manifestations without .xml in the name") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "01", "first"), ("LOG_0002", "02", "second.xml"))
    ).toString()
    val manifestations = Map(
      "first.xml" -> Some(
        metsXmlWith(
          "b30246039",
          license = Some(License.InCopyright),
          fileSec = fileSec("b30246039"),
          structMap = structMap)),
      "second.xml" -> Some(metsXmlWith("b30246039")),
    )
    transform(Some(xml), manifestations) shouldBe Right(
      MetsData(
        recordIdentifier = "b30246039",
        accessConditionDz = Some("INC"),
        accessConditionStatus = None,
        fileReferences = createFileReferences(2, "b30246039")
      )
    )
  }

  it("should error if first manifestation doesn't exist in store") {
    val xml = loadXmlFile("/b22012692.xml")
    val manifestations = Map(
      "b22012692_0003.xml" -> Some(loadXmlFile("/b22012692_0003.xml")),
      "b22012692_0001.xml" -> None,
    )
    transform(Some(xml), manifestations) shouldBe a[Left[_, _]]
  }

  def transform(root: Option[String],
                manifestations: Map[String, Option[String]] = Map.empty) = {

    val metsLocation = MetsLocation(
      "bucket",
      "path",
      1,
      if (root.nonEmpty) "root.xml" else "nonexistent.xml",
      manifestations.toList.map { case (file, _) => file }
    )

    val store = new MemoryStore(
      (manifestations ++ root
        .map(content => "root.xml" -> Some(content))).collect {
        case (file, Some(content)) =>
          ObjectLocation("bucket", s"path/$file") -> content
      }
    )

    new MetsXmlTransformer(store).transform(metsLocation)
  }

  def createFileReferences(n: Int,
                           bumber: String,
                           manifestN: Option[Int] = None): List[FileReference] =
    (1 to n).toList.map { i =>
      FileReference(
        f"FILE_$i%04d_OBJECTS",
        manifestN match {
          case None    => f"$bumber%s_$i%04d.jp2"
          case Some(n) => f"$bumber%s_$n%04d_$i%04d.jp2"
        },
        Some("image/jp2")
      )
    }

  def loadXmlFile(path: String) =
    IOUtils.toString(getClass.getResourceAsStream(path), "UTF-8")

  def createIds(n: Int): List[String] =
    (1 to n).map { idx =>
      f"FILE_$idx%04d_OBJECTS"
    }.toList
}
