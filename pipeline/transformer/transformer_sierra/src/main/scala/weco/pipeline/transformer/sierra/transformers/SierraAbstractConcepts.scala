package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.pipeline.transformer.marc_common.transformers.MarcHasRecordControlNumber
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions

trait SierraAbstractConcepts
    extends Logging
    with MarcHasRecordControlNumber
    with SierraMarcDataConversions
