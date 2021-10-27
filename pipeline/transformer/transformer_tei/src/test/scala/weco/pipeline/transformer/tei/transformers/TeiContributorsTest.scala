package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.tei.generators.TeiGenerators

class TeiContributorsTest extends AnyFunSpec with TeiGenerators with Matchers
with EitherValues{
  val id = "manuscript_15651"
  it("extracts author from msItem"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(label = "John Wick"))), isFihrist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person("John Wick"), List(ContributionRole("author"))))
  }

  it("fails extracting authors if the label is empty"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(label = ""))),isFihrist = false)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include ("label")
  }

  it("extracts ids for authors"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(label = "Dominic Toretto", key = Some("12534")))), isFihrist = false)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Dominic Toretto", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12534"))), List(ContributionRole("author"))))
  }
  it("if the id exists but it's an empty string, it extracts the author but doesn't add an id"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(label = "Frank Martin", key = Some("")))), isFihrist = false)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person("Frank Martin"), List(ContributionRole("author"))))
  }
  it("extracts author from persName"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(persNames = List(persName(label = "John Connor", key = Some("12345"))), key = None))), isFihrist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "John Connor", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }

  it("extracts persName with type=original when there are multiple persName nodes"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "John Connor", key = Some("54321"), `type` = Some("something else")),
      persName(label = "Sarah Connor", key = Some("12345"), `type` = Some("original"))), key = None))), isFihrist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }

  it("errors if there are multiple persName nodes and none have type=original"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "John Connor", key = Some("54321"), `type` = Some("something else")), persName(label = "Sarah Connor", key = Some("12345"))), key = None))), isFihrist = false)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include ("persName")
  }

  it("errors if there are multiple persName nodes and more than one have type=original"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "John Connor", key = Some("54321"), `type` = Some("original")), persName(label = "Sarah Connor", key = Some("12345"), `type` = Some("original"))), key = None))), isFihrist = false)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include ("persName")
  }

  it("gets the person id from the author node if it's not on persName node"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(key = Some("12345"),persNames = List(
          persName(label = "Ellen Ripley", `type` = Some("original")))))), isFihrist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Ellen Ripley", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }
  it("gets the person id from the author node if it's not on persName node -multiple persName nodes"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(key = Some("12345"),persNames = List(
          persName(label = "Sarah Connor", `type` = Some("original")),persName(label = "T 800"))))), isFihrist = false)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.VIAF, "Person", "12345"))), List(ContributionRole("author"))))
  }
  it("if the manuscript is part of the Fihrist catalogue, it extracts authors ids as the fihirist identifier type"){
    val result = TeiContributors.authors(node = msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "Sarah Connor",key = Some("12345"))), key = None))), isFihrist = true)

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.Fihrist, "Person", "12345"))), List(ContributionRole("author"))))
  }


  describe("scribe") {
    it("extracts a single scribe from handNote/persName") {
      val result = TeiContributors.scribes(teiXml(id, handNotes = List(handNotes(persNames = List(scribe("Tony Stark"))))), None)

      result shouldBe List(Contributor(Person("Tony Stark"), List(ContributionRole("scribe"))))
    }
    it("extracts a list of scribes from handNote/persName") {
      val result = TeiContributors.scribes(teiXml(id, handNotes = List(handNotes(persNames = List(scribe("Tony Stark"), scribe("Peter Parker"), scribe("Steve Rogers"))))), None)

      result shouldBe List(Contributor(Person("Tony Stark"), List(ContributionRole("scribe"))),Contributor(Person("Peter Parker"), List(ContributionRole("scribe"))),Contributor(Person("Steve Rogers"), List(ContributionRole("scribe"))))
    }
    it("doesn't extract a contributor from from handNote/persName if it doesn't have role=scr"){
      val result = TeiContributors.scribes(teiXml(id, handNotes = List(handNotes(persNames = List(persName("Clark Kent"))))), None)

      result shouldBe Nil
    }
    it("extracts scribes from handNote with scribe attribute"){
      val result = TeiContributors.scribes(teiXml(id, handNotes = List(handNotes(label = "Steve Rogers", scribe = Some("sole")),handNotes(label = "Bruce Banner", scribe = Some("sole")))), None)

      result shouldBe List(Contributor(Person("Steve Rogers"), List(ContributionRole("scribe"))),Contributor(Person("Bruce Banner"), List(ContributionRole("scribe"))))
    }
    it("doesn't return the scribes if there is an item id on the xml but not on the argument"){
      val itemId = s"#${id}_item1"
      val result = TeiContributors.scribes(xml = teiXml(id,
        handNotes = List(
          handNotes(label = "Wanda Maximoff", scribe = Some("sole"), locus = Some(locus(label = "p 22-24", target = Some(itemId))))
        ), items = List(msItem(id = itemId))), target = None)

      result shouldBe Nil

    }
    it("only returns scribes with that item id if a target is passed"){
      val itemId = s"${id}_item1"
      val result = TeiContributors.scribes(xml = teiXml(id,
        handNotes = List(
          handNotes(label = "Wanda Maximoff", scribe = Some("sole"), locus = Some(locus(label = "p 22-24", target = Some(s"#$itemId"))))
        ), items = List(msItem(id = itemId))),
        Some(itemId))

      result shouldBe List(Contributor(Person("Wanda Maximoff"), List(ContributionRole("scribe"))))

    }

    // test multiple locus tags in handNote
    // test that items don't get scribes for other items
    // test that items don't get scribes for the wrapperwork
    // test that items don't get scribes in persName
    // test multiple ids in scribes ids
    // test multiple persNames
    // test multiple handNote tags
  }
}
