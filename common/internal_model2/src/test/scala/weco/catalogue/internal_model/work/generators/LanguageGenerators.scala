package weco.catalogue.internal_model.work.generators

import org.scalacheck.Arbitrary
import weco.catalogue.internal_model.languages.Language
import weco.fixtures.RandomGenerators

trait LanguageGenerators extends RandomGenerators {
  // We have a rule that says language codes should be exactly 3 characters long
  implicit val arbitraryLanguage: Arbitrary[Language] =
    Arbitrary {
      createLanguage
    }

  def createLanguage: Language =
    Language(
      id = randomAlphanumeric(length = 3),
      label = randomAlphanumeric()
    )
}
