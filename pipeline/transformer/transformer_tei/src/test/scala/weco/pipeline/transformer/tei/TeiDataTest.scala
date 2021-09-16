package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.Format.ArchivesAndManuscripts
import weco.catalogue.internal_model.work.{
  InternalWork,
  MergeCandidate,
  Work,
  WorkData
}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.sierra.generators.SierraIdentifierGenerators

import java.time.Instant

class TeiDataTest
    extends AnyFunSpec
    with SierraIdentifierGenerators
    with Matchers
    with IdentifiersGenerators {
  it("transforms into a work") {
    val title = "This is the title"
    val bnumber = createSierraBibNumber.withCheckDigit
    val description = Some("This is the description")
    val languages = List(Language("ara", "Arabic"))
    val id = "id"
    val teiData = TeiData(
      id = id,
      title = title,
      bNumber = Some(bnumber),
      description = description,
      languages = languages
    )

    val version = 1
    val modifiedTime = Instant.now()
    val work = teiData.toWork(modifiedTime, version)

    val mergeCandidate = MergeCandidate(
      createSierraSystemSourceIdentifierWith(value = bnumber),
      reason = "Bnumber present in TEI file"
    )
    val source = Source(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.Tei,
        ontologyType = "Work",
        value = id
      ),
      sourceModifiedTime = modifiedTime
    )
    work shouldBe Work.Visible[Source](
      version,
      WorkData[Unidentified](
        title = Some(title),
        mergeCandidates = List(mergeCandidate),
        description = description,
        languages = languages,
        format = Some(ArchivesAndManuscripts)
      ),
      state = source
    )
  }
  it("does not create mergeCandidates if the bnumber is invalid") {
    val teiData = TeiData(
      id = "id",
      title = "This is the title",
      bNumber = Some("fjhsdg"),
      description = Some("This is the description"),
      languages = List(Language("ara", "Arabic"))
    )

    val work = teiData.toWork(Instant.now(), 1)

    work.data.mergeCandidates shouldBe empty
  }
  it("transforms multiple internal TeiData into internalWorks") {
    val firstInnerTeiData = TeiData(
      id = "id_1",
      title = "This is the first item title",
      description = Some("This is the first item description"),
      languages = List(Language("ara", "Arabic"))
    )
    val secondInnerTeiData = TeiData(
      id = "id_2",
      title = "This is the second item title",
      description = Some("This is the second item description"),
      languages = List(Language("ara", "Arabic"))
    )
    val teiData = TeiData(
      id = "id",
      title = "This is the title",
      bNumber = Some("fjhsdg"),
      description = Some("This is the description"),
      nestedTeiData = List(firstInnerTeiData, secondInnerTeiData)
    )

    val work = teiData.toWork(Instant.now(), 1)

    val firstInternalWorkStub = InternalWork.Source(
      sourceIdentifier =
        SourceIdentifier(IdentifierType.Tei, "Work", firstInnerTeiData.id),
      workData = WorkData(
        title = Some(firstInnerTeiData.title),
        languages = firstInnerTeiData.languages,
        description = firstInnerTeiData.description,
        format = Some(ArchivesAndManuscripts)
      )
    )

    val secondInternalWorkStub = InternalWork.Source(
      sourceIdentifier =
        SourceIdentifier(IdentifierType.Tei, "Work", secondInnerTeiData.id),
      workData = WorkData(
        title = Some(secondInnerTeiData.title),
        languages = secondInnerTeiData.languages,
        description = secondInnerTeiData.description,
        format = Some(ArchivesAndManuscripts)
      )
    )
    work.state.internalWorkStubs shouldBe List(
      firstInternalWorkStub,
      secondInternalWorkStub)
  }

  describe("if there's a single inner data") {
    it("uses the title of the item") {
      val innerTeiData = TeiData(
        id = "id_1",
        title = "This is the item title"
      )
      val teiData = TeiData(
        id = "id",
        title = "This is the top-level title",
        nestedTeiData = List(innerTeiData)
      )

      val work = teiData.toWork(Instant.now(), 1)

      work.state.internalWorkStubs shouldBe empty
      work.data.title shouldBe Some(innerTeiData.title)
    }
  }
}
