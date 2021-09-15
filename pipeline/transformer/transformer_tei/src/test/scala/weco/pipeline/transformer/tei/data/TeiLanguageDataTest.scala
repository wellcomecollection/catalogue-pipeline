package weco.pipeline.transformer.tei.data

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.tei.transformers.TeiLanguages

import java.io.FileInputStream
import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import scala.collection.JavaConverters._
import scala.xml.XML

class TeiLanguageDataTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  val testCases = Table(
    ("id", "label", "expectedLanguage"),
    ("ar", "Arabic", Language(id = "ara", label = "Arabic")),
    ("jv", "Javanese", Language(id = "jav", label = "Javanese")),
    (
      "grc",
      "Ancient Greek",
      Language(id = "grc", label = "Greek, Ancient (to 1453)")),
    ("btk", "Toba-Batak", Language(id = "btk", label = "Toba-Batak"))
  )

  it("handles the test cases") {
    forAll(testCases) {
      case (id, label, expectedLanguage) =>
        TeiLanguageData(id = id, label = label) shouldBe Right(
          expectedLanguage)
    }
  }

  it("fails if it sees an unexpected language") {
    TeiLanguageData(id = "xyz", label = "???") shouldBe a[Left[_,_]]
  }

  /** This test is to help you find languages in the TEI files that aren't currently
    * mapped by TeiLanguageData.  It walks a local checkout of the TEI repo, tries
    * to extract all the language codes, and warns for anything it can't map.
    *
    * This is possible because the TEI repository is small compared to other sources,
    * and can be saved in a single snapshot.
    *
    * This test is provided for the benefit of other devs working on this code, not
    * something to run continually in CI.  Additionally, it may not be possible for us
    * to get this running cleanly on our own -- if it flags inconsistent data, that
    * should be fixed in the source files rather than a workaround in our code.
    *
    */
  ignore("handles all the TEI languages") {
    val root = Paths.get("/Users/alexwlchan/repos", "wellcome-collection-tei")
    val xmlPaths =
      Files
        .walk(root)
        .collect(Collectors.toList[Path])
        .asScala
        .filter { Files.isRegularFile(_) }
        .filter { _.getFileName.toString.endsWith(".xml") }

    xmlPaths.foreach { p =>
      val xml = XML.load(new FileInputStream(p.toAbsolutePath.toString))

      TeiLanguages.parseLanguages((xml)) match {
        case Right(nodes) => nodes
        case Left(err) =>
          println(s"$p: error while reading <textLang> nodes: $err")
          Seq[(String, String)]()
      }

    }
  }
}
