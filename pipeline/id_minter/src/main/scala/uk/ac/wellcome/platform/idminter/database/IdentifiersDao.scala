package uk.ac.wellcome.platform.idminter.database

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.storage.Dao

trait IdentifiersDao extends Dao[SourceIdentifier, Identifier]
