package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.{Inspectors, LoneElement}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.{Agent, Person}
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcContributorsTest
    extends AnyFunSpec
    with Matchers
    with Inspectors
    with TableDrivenPropertyChecks
    with LoneElement {

  private implicit val ctx: LoggingContext = LoggingContext("")
  describe(
    "extracting contributors from Main Entry (1xx) and Added Entry (7xx) fields"
  ) {
    info("https://www.loc.gov/marc/bibliographic/bd1xx.html")
    info("https://www.loc.gov/marc/bibliographic/bd70x75x.html")
    describe("When there are no contributors") {
      it("returns nothing if no relevant fields are present") {
        MarcContributors(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "245",
                subfields = Seq(MarcSubfield(tag = "a", content = "The Title"))
              )
            )
          )
        ) shouldBe Nil
      }

      it("returns nothing if the only relevant fields are invalid") {
        MarcContributors(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "110",
                subfields = Nil
              ),
              MarcField(
                marcTag = "100",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "   "),
                  MarcSubfield(tag = "b", content = ""),
                  MarcSubfield(tag = "c", content = "  ")
                )
              )
            )
          )
        ) shouldBe Nil
      }

    }

    describe("When there are contributors to transform") {
      it("returns a mix of primary and non-primary contributors") {
        val contributors = MarcContributors(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "100",
                subfields = Seq(MarcSubfield(tag = "a", content = "Euripedes"))
              ),
              MarcField(
                marcTag = "110",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Felpersham University")
                )
              ),
              MarcField(
                marcTag = "111",
                subfields =
                  Seq(MarcSubfield(tag = "a", content = "Council of Elrond"))
              ),
              MarcField(
                marcTag = "700",
                subfields =
                  Seq(MarcSubfield(tag = "a", content = "Anil Mendem"))
              ),
              MarcField(
                marcTag = "710",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "University of Inverdoon")
                )
              ),
              MarcField(
                marcTag = "711",
                subfields = Seq(
                  MarcSubfield(
                    tag = "a",
                    content = "Xenophon's Symposium"
                  )
                )
              )
            )
          )
        )
        contributors.map(_.primary) should contain theSameElementsAs Seq(
          true, true, true, false, false, false
        )
        contributors.map(_.agent.label) should contain theSameElementsAs Seq(
          "Euripedes",
          "Felpersham University",
          "Council of Elrond",
          "Anil Mendem",
          "University of Inverdoon",
          "Xenophon's Symposium"
        )
      }
      it(
        "returns primary contributors first, but otherwise respects document order"
      ) {
        val contributors = MarcContributors(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "711",
                subfields = Seq(
                  MarcSubfield(
                    tag = "a",
                    content = "Xenophon's Symposium"
                  )
                )
              ),
              MarcField(
                marcTag = "700",
                subfields =
                  Seq(MarcSubfield(tag = "a", content = "Anil Mendem"))
              ),
              MarcField(
                marcTag = "100",
                subfields = Seq(MarcSubfield(tag = "a", content = "Euripedes"))
              ),
              MarcField(
                marcTag = "710",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "University of Inverdoon")
                )
              ),
              MarcField(
                marcTag = "110",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Felpersham University")
                )
              ),
              MarcField(
                marcTag = "111",
                subfields =
                  Seq(MarcSubfield(tag = "a", content = "Council of Elrond"))
              )
            )
          )
        )
        contributors.map(_.primary) should contain theSameElementsAs Seq(
          true, true, true, false, false, false
        )
        contributors.map(_.agent.label) should contain theSameElementsAs Seq(
          "Euripedes",
          "Felpersham University",
          "Council of Elrond",
          "Xenophon's Symposium",
          "Anil Mendem",
          "University of Inverdoon"
        )
      }

      it(
        "returns an Agent contributor if a Person field contains subfield t: 'Title of a work'"
      ) {
        info(
          "This is a historic quirk, it may not need to be this way, and it's probably incorrect"
        )
        val contributors = MarcContributors(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "700",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                  MarcSubfield(tag = "t", content = "Codex Regius")
                )
              ),
              MarcField(
                marcTag = "100",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Snorri Sturluson"),
                  MarcSubfield(tag = "t", content = "Gylfaginning")
                )
              )
            )
          )
        )
        contributors.head.agent should be(an[Agent[_]])
        contributors(1).agent should be(an[Agent[_]])
      }

      it("harmonises Agent entries with Person entries if they match") {
        info("The first two fields generate Agents due to the t subfield")
        info(
          "However, because fields with the same ID later generate a Person"
        )
        info("the ontologytype harmonisation converts them both to a Person")
        val contributors = MarcContributors(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "100",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                  MarcSubfield(tag = "t", content = "Codex Regius"),
                  MarcSubfield(tag = "0", content = "n97018003")
                )
              ),
              MarcField(
                marcTag = "100",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Snorri Sturluson"),
                  MarcSubfield(tag = "t", content = "Gylfaginning"),
                  MarcSubfield(tag = "0", content = "n50000553")
                )
              ),
              MarcField(
                marcTag = "700",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                  MarcSubfield(tag = "0", content = "n97018003")
                )
              ),
              MarcField(
                marcTag = "700",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Snorri Sturluson"),
                  MarcSubfield(tag = "0", content = "n50000553")
                )
              )
            )
          )
        )
        forAll(contributors) {
          contributor =>
            contributor.agent should be(a[Person[_]])
        }
      }
      describe("different agent types") {
        forAll(
          Table(
            ("tag", "agentType"),
            ("100", "Person"),
            ("700", "Person"),
            ("110", "Organisation"),
            ("710", "Organisation"),
            ("111", "Meeting"),
            ("711", "Meeting")
          )
        ) {
          (tag, agent_type) =>
            it(s"extracts an agent of type $agent_type from field $tag") {
              MarcContributors(
                MarcTestRecord(fields =
                  Seq(
                    MarcField(
                      marcTag = tag,
                      subfields = Seq(
                        MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                        MarcSubfield(tag = "e", content = "E"),
                        MarcSubfield(tag = "j", content = "J")
                      )
                    )
                  )
                )
              ).loneElement.agent.getClass.getName
                .split('.')
                .last shouldBe agent_type
            }

        }
      }
      describe("contribution roles") {
        forAll(
          Table(
            ("tag", "role_subfields", "expected_roles"),
            ("100", "e,j", "E J"),
            ("700", "e,j", "E J"),
            ("110", "e", "E"),
            ("710", "e", "E"),
            ("111", "j", "J"),
            ("711", "j", "J")
          )
        ) {
          (tag, role_subfields, expected_roles) =>
            it(
              s"extracts the contribution role from field $tag from subfield(s) $role_subfields"
            ) {
              MarcContributors(
                MarcTestRecord(fields =
                  Seq(
                    MarcField(
                      marcTag = tag,
                      subfields = Seq(
                        MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                        MarcSubfield(tag = "e", content = "E"),
                        MarcSubfield(tag = "j", content = "J")
                      )
                    )
                  )
                )
              ).loneElement.roles
                .map(_.label)
                .mkString(" ") shouldBe expected_roles
            }
        }
      }
      describe("deduplication") {

        it(
          "returns only the primary contributor if that contributor is duplicated as an Added Entry"
        ) {
          val contributors = MarcContributors(
            MarcTestRecord(fields =
              Seq(
                MarcField(
                  marcTag = "100",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                    MarcSubfield(tag = "0", content = "n97018003")
                  )
                ),
                MarcField(
                  marcTag = "100",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Snorri Sturluson"),
                    MarcSubfield(tag = "0", content = "n50000553")
                  )
                ),
                MarcField(
                  marcTag = "700",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Sæmundr fróði"),
                    MarcSubfield(tag = "0", content = "n97018003")
                  )
                ),
                MarcField(
                  marcTag = "700",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Snorri Sturluson"),
                    MarcSubfield(tag = "0", content = "n50000553")
                  )
                )
              )
            )
          )
          contributors should have length 2
          contributors.map(_.agent.label) should contain theSameElementsAs Seq(
            "Sæmundr fróði",
            "Snorri Sturluson"
          )
          forAll(contributors) {
            contributor => contributor.primary shouldBe true
          }
        }

        it(
          "deduplicates by matching on the whole contributor"
        ) {
          info("It is only the difference in primary status that is ignored")
          info("when finding and eliminating duplicate contributors")

          val fields = Seq(
            MarcField(
              marcTag = "100",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "Max Bialystock"),
                MarcSubfield(tag = "e", content = "producer")
              )
            ),
            MarcField(
              // differs by presence of id
              marcTag = "700",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "Max Bialystock"),
                MarcSubfield(tag = "0", content = "no2021024802"),
                MarcSubfield(tag = "e", content = "producer")
              )
            ),
            MarcField(
              // is the same
              marcTag = "700",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "Max Bialystock"),
                MarcSubfield(tag = "e", content = "producer")
              )
            ),
            MarcField(
              // differs by having a different role
              marcTag = "700",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "Max Bialystock"),
                MarcSubfield(tag = "e", content = "director")
              )
            ),
            MarcField(
              // differs by having no role
              marcTag = "700",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "Max Bialystock")
              )
            ),
            MarcField(
              // is the same
              marcTag = "700",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "Max Bialystock"),
                MarcSubfield(tag = "e", content = "producer")
              )
            )
          )

          val contributors = MarcContributors(MarcTestRecord(fields = fields))
          contributors should have length 4
          // not really an assertion, just to clarify that the two
          // 700s that are otherwise identical to the 100 are discarded
          fields should have length 6
        }

        it(
          "Also removes fully identical duplicates"
        ) {
          val Seq(primary, secondary) = MarcContributors(
            MarcTestRecord(fields =
              Seq(
                MarcField(
                  marcTag = "100",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Sæmundr fróði")
                  )
                ),
                MarcField(
                  marcTag = "100",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Sæmundr fróði")
                  )
                ),
                MarcField(
                  marcTag = "700",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Snorri Sturluson")
                  )
                ),
                MarcField(
                  marcTag = "700",
                  subfields = Seq(
                    MarcSubfield(tag = "a", content = "Snorri Sturluson")
                  )
                )
              )
            )
          )

          primary.primary shouldBe true
          primary.agent.label shouldBe "Sæmundr fróði"

          secondary.primary shouldBe false
          secondary.agent.label shouldBe "Snorri Sturluson"

        }
      }
    }

  }

}
