from models.pipeline.serialisable import SerialisableModel


class Type(SerialisableModel):
    type: str


class AccessStatusRelationship(Type):
    pass


class AccessMethod(Type):
    pass


ViewOnline = AccessMethod(type="ViewOnline")

#  TODO: (Resource vs RelatedResource)
#  Scala code says this is not exposed the public API,
#  but we need it for the "available online" filter.
#  I wonder if that is actually true, and we can get
#  rid of the whole thing?
#  All EBSCO records are currently "Resource"
Resource = AccessStatusRelationship(type="Resource")


class AccessStatus(Type):
    relationship: AccessStatusRelationship | None = None


LicensedResource = AccessStatus(type="LicensedResources", relationship=Resource)


class AccessCondition(SerialisableModel):
    method: Type
    status: AccessStatus | None = None
    terms: str | None = None
    note: str | None = None
