package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[Id <: WithSourceIdentifier, DataId <: IdState]{
  val id : Id
  val ontologyType: String
}

case class SourceWorks[Id <: WithSourceIdentifier, DataId <: IdState](
                                                  canonicalWork: SourceWork[Id, DataId]
                                                 , redirectedWork: Option[SourceWork[Id, DataId]]
) extends ImageSource[Id, DataId]{
  override val id = canonicalWork.id
  override val ontologyType: String = canonicalWork.ontologyType
}

case class SourceWork[Id <: WithSourceIdentifier, DataId <: IdState](
                                                 id: Id,
                                               data: WorkData[DataId, Id],
                                                   ontologyType: String = "Work",
                                                 )


