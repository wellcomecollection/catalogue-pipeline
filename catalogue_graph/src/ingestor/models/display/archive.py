from models.pipeline.serialisable import ElasticsearchModel

from .id_label import DisplayIdLabel


class DisplayArchive(ElasticsearchModel):
    """Information about the archive a work belongs to.

    Only works whose collection path label starts with a recognised
    prefix (e.g. "PP/EBC") are considered to be part of an archive.
    """

    category: DisplayIdLabel
