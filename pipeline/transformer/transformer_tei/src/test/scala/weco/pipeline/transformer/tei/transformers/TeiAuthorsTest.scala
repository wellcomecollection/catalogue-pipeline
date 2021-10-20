package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.tei.generators.TeiGenerators

class TeiAuthorsTest extends AnyFunSpec with TeiGenerators with Matchers
with EitherValues{
  val id = "manuscript_15651"
  it("extracts author from msItem"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(label = "John Wick"))), isFihirist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person("John Wick"), List(ContributionRole("author"))))
  }

  it("fails extracting authors if the label is empty"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(label = ""))),isFihirist = false)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include ("label")
  }

  it("extracts ids for authors"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(label = "Dominic Toretto", key = Some("12534")))), isFihirist = false)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Dominic Toretto", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12534"))), List(ContributionRole("author"))))
  }
  it("if the id exists but it's an empty string, it extracts the author but doesn't add an id"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(label = "Frank Martin", key = Some("")))), isFihirist = false)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person("Frank Martin"), List(ContributionRole("author"))))
  }
  it("extracts author from persName"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(persNames = List(persName(label = "John Connor", key = Some("12345"))), key = None))), isFihirist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "John Connor", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }

  it("extracts persName with type=original when there are multiple persName nodes"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "John Connor", key = Some("54321"), `type` = Some("something else")),
      persName(label = "Sarah Connor", key = Some("12345"), `type` = Some("original"))), key = None))), isFihirist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }

  it("errors if there are multiple persName nodes and none have type=original"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "John Connor", key = Some("54321"), `type` = Some("something else")), persName(label = "Sarah Connor", key = Some("12345"))), key = None))), isFihirist = false)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include ("persName")
  }

  it("errors if there are multiple persName nodes and more than one have type=original"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "John Connor", key = Some("54321"), `type` = Some("original")), persName(label = "Sarah Connor", key = Some("12345"), `type` = Some("original"))), key = None))), isFihirist = false)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include ("persName")
  }

  it("gets the person id from the author node if it's not on persName node"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(key = Some("12345"),persNames = List(
          persName(label = "Ellen Ripley", `type` = Some("original")))))), isFihirist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Ellen Ripley", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }
  it("gets the person id from the author node if it's not on persName node -multiple persName nodes"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(key = Some("12345"),persNames = List(
          persName(label = "Sarah Connor", `type` = Some("original")),persName(label = "T 800"))))), isFihirist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }
  it("if the manuscript is part of the Fihrist catalogue, it extracts authors ids as the fihirtist identifier type"){
    val result = TeiAuthors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "Sarah Connor",key = Some("12345"))), key = None))), isFihirist = true)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.Fihrist, "Person", "12345"))), List(ContributionRole("author"))))
  }
}
