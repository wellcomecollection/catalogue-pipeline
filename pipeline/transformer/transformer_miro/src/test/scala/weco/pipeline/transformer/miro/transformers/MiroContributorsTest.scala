package weco.pipeline.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.Assertion
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Agent, Contributor}
import weco.pipeline.transformer.miro.generators.MiroRecordGenerators
import weco.pipeline.transformer.miro.source.MiroRecord

class MiroContributorsTest
    extends AnyFunSpec
    with MiroRecordGenerators
    with MiroTransformableWrapper {
  it("has no contributors if no image_creator field is present") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        creator = None
      ),
      expectedContributors = List()
    )
  }

  it("passes through a single value in the image_creator field") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        creator = Some(List(Some("Researcher Rosie")))
      ),
      expectedContributors = List(
        ExpectedContributor(
          label = "Researcher Rosie",
          idLabel = "researcher rosie")
      )
    )
  }

  it("ignores null values in the image_creator field") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        creator =
          Some(List(Some("Beekeeper Brian"), None, Some("Dog-owner Derek")))
      ),
      expectedContributors = List(
        ExpectedContributor(
          label = "Beekeeper Brian",
          idLabel = "beekeeper brian"),
        ExpectedContributor(
          label = "Dog-owner Derek",
          idLabel = "dog-owner derek")
      )
    )
  }

  it("passes through a single value in the image_creator_secondary field") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        secondaryCreator = Some(List("Scientist Sarah"))
      ),
      expectedContributors = List(
        ExpectedContributor(
          label = "Scientist Sarah",
          idLabel = "scientist sarah")
      )
    )
  }

  it("passes through multiple values in the image_creator_secondary field") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        secondaryCreator =
          Some(List("Gamekeeper Gordon", "Herpetologist Harriet"))
      ),
      expectedContributors = List(
        ExpectedContributor(
          label = "Gamekeeper Gordon",
          idLabel = "gamekeeper gordon"),
        ExpectedContributor(
          label = "Herpetologist Harriet",
          idLabel = "herpetologist harriet")
      )
    )
  }

  it("combines the image_creator and image_secondary_creator fields") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        creator = Some(List(Some("Mycologist Morgan"))),
        secondaryCreator = Some(List("Manufacturer Mel"))
      ),
      expectedContributors = List(
        ExpectedContributor(
          label = "Mycologist Morgan",
          idLabel = "mycologist morgan"),
        ExpectedContributor(
          label = "Manufacturer Mel",
          idLabel = "manufacturer mel")
      )
    )
  }

  it("passes through a value from the image_source_code field") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        sourceCode = Some("GAV")
      ),
      expectedContributors = List(
        ExpectedContributor(
          label = "Isabella Gavazzi",
          idLabel = "isabella gavazzi")
      )
    )
  }

  it("does not use the image_source_code field for Wellcome Collection") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        sourceCode = Some("WEL")
      ),
      expectedContributors = List()
    )
  }

  it("combines the image_creator and image_source_code fields") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        creator = Some(List(Some("Sally Snake"))),
        sourceCode = Some("SNL")
      ),
      expectedContributors = List(
        ExpectedContributor(label = "Sally Snake", idLabel = "sally snake"),
        ExpectedContributor(label = "Sue Snell", idLabel = "sue snell")
      )
    )
  }

  it("prefers the image_credit_line to image_source_code") {
    transformRecordAndCheckContributors(
      miroRecord = createMiroRecordWith(
        creditLine = Some("Anna Gordon, Fernan Federici & Jim Haseloff"),
        sourceCode = Some("FED")
      ),
      expectedContributors = List(
        ExpectedContributor(label = "Anna Gordon, Fernan Federici & Jim Haseloff", idLabel = "anna gordon, fernan federici jim haseloff"),
      )
    )
  }

  case class ExpectedContributor(label: String, idLabel: String)

  private def transformRecordAndCheckContributors(
    miroRecord: MiroRecord,
    expectedContributors: List[ExpectedContributor]
  ): Assertion = {
    val transformedWork = transformWork(miroRecord)
    transformedWork.data.contributors shouldBe expectedContributors.map {
      case ExpectedContributor(label, idLabel) =>
        Contributor(
          agent = Agent(
            id = IdState.Identifiable(
              sourceIdentifier = SourceIdentifier(
                identifierType = IdentifierType.LabelDerived,
                value = idLabel,
                ontologyType = "Agent"
              )
            ),
            label = label
          ),
          roles = Nil
        )
    }
  }
}
