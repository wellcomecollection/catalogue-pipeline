package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.tei.generators.TeiGenerators

class TeiPhysicalDescriptionTest
    extends AnyFunSpec
    with TeiGenerators
    with Matchers {
  val id = "id"

  it("extracts material from physical description for the wrapper work") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc = Some(objectDesc(material = Some("paper"))))
        )
      )
    )

    result shouldBe Some("Material: paper")
  }

  it("extracts material description for the wrapper work") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                None,
                support = Some(
                  support("Multiple manuscript parts collected in one volume.")
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some("Multiple manuscript parts collected in one volume.")
  }

  it("extracts watermark") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                None,
                support = Some(
                  support(
                    supportLabel =
                      "Multiple manuscript parts collected in one volume.",
                    watermarks = List(watermark("Blih bluh blah"))
                  )
                )
              )
            )
          )
        )
      )
    )
    result shouldBe Some(
      "Multiple manuscript parts collected in one volume.; Watermarks: Blih bluh blah"
    )
  }

  it("extracts extent for the wrapper work") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                support = None,
                extent = Some(
                  extent(
                    label = "3 pages",
                    dimensions = List(
                      dimensions(
                        unit = "mm",
                        `type` = "leaf",
                        height = "100",
                        width = "300"
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some(
      "3 pages; leaf dimensions: width 300 mm, height 100 mm"
    )
  }

  it("supports multiple dimensions blocks within extent for the wrapper work") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                support = None,
                extent = Some(
                  extent(
                    label = "3 pages",
                    dimensions = List(
                      dimensions(
                        unit = "mm",
                        `type` = "leaf",
                        height = "100",
                        width = "300"
                      ),
                      dimensions(
                        unit = "mm",
                        `type` = "text",
                        height = "90",
                        width = "290"
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some(
      "3 pages; leaf dimensions: width 300 mm, height 100 mm; text dimensions: width 290 mm, height 90 mm"
    )
  }

  it("extracts physicalDescription for parts") {
    val result = TeiPhysicalDescription(
      List(
        msPart(
          id = "",
          physDesc = Some(
            physDesc(objectDesc =
              Some(
                objectDesc(
                  None,
                  support = Some(
                    support(
                      "Multiple manuscript parts collected in one volume."
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
    result shouldBe Some("Multiple manuscript parts collected in one volume.")
  }

  it("doesn't add the unit if it's already in the dimension label") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                support = None,
                extent = Some(
                  extent(
                    label = "3 pages",
                    dimensions = List(
                      dimensions(
                        unit = "mm",
                        `type` = "leaf",
                        height = "100mm",
                        width = "300mm"
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some("3 pages; leaf dimensions: width 300mm, height 100mm")
  }

  it("normalises the extent text") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                extent = Some(
                  extent(
                    label = """3 pages
                |and 5 leaves
                |""".stripMargin
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some("3 pages and 5 leaves")
  }

  it("normalises the support text") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                support = Some(support("""Multiple manuscript
              |parts collected in
              |one volume.""".stripMargin))
              )
            )
          )
        )
      )
    )

    result shouldBe Some("Multiple manuscript parts collected in one volume.")
  }

  it("does not extract dimensions if they are empty") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                support = None,
                extent = Some(
                  extent(
                    label = "3 pages",
                    dimensions = List(<dimensions>
            </dimensions>)
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some("3 pages")
  }

  it("does not extract physical description if it's empty") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(objectDesc(material = None, support = None, extent = None))
          )
        )
      )
    )

    result shouldBe None
  }

  it("can parse dimensions in the dim format") {
    val result = TeiPhysicalDescription(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                material = None,
                support = None,
                extent = Some(
                  extent(
                    label = "",
                    dimensions = List(<dimensions unit="cm">
              <dim type="width">35</dim>
              <dim type="length">35</dim>
            </dimensions>)
                  )
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Some("dimensions: width 35 cm, length 35 cm")
  }
}
