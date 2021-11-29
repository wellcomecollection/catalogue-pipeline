//package weco.pipeline.transformer.transformers
//
//import org.scalatest.funspec.AnyFunSpec
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.prop.TableDrivenPropertyChecks
//
//class NotesTransformerTest extends AnyFunSpec with Matchers with TableDrivenPropertyChecks {
//  val transformer = new NotesTransformer {}
//
//  describe("createLocationOfDuplicatesNote") {
//    it("removes a note which just describes digitisation") {
//      val digitisationNotes = Table(
//        "CopiesText",
//        "A digitised copy is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics.",
//        "A digitised copy is held by the Wellcome Library.",
//        "A digitised copy is held by the Wellcome Library as part of the Codebreakers: Makers of Modern Genetics programme.",
//        "A digitised copy is held by the Wellcome Library as part of The Mental Health Archives digitisation project.",
//        "A digitised copy is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics. ",
//        "A digitised copy is held by the Wellcome Library as part of The Mental Health Archives digitisation project",
//        "A digitised copy is held by the Wellcome Library as part of The Mental Health Archives digitisation project. ",
//        "A digitised copy is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics",
//        "A digitised copy is held by the Wellcome Library as part of The Mental Health Archives digitisation project..",
//      )
//
//      forAll(digitisationNotes) {
//        transformer.createLocationOfDuplicatesNote(_) shouldBe None
//      }
//    }
//  }
//}
