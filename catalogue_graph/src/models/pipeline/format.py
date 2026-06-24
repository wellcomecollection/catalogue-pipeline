from models.pipeline.id_label import IdLabel

FORMAT_LABEL_MAPPING = {
    "a": "Books",
    "q": "Digital Images",
    "l": "Ephemera",
    "e": "Maps",
    "k": "Pictures",
    "w": "Student dissertations",
    "r": "3-D Objects",
    "m": "CD-Roms",
    "d": "Journals",
    "p": "Mixed materials",
    "i": "Audio",
    "g": "Videos",
    "h": "Archives and manuscripts",
    "hdig": "Born-digital archives",
    "n": "Film",
    "b": "Manuscripts",
    "c": "Music",
    "u": "Standing order",
    "z": "Web sites",
    "v": "E-books",
    "s": "E-sound",
    "j": "E-journals",
    "f": "E-videos",
    "x": "Manuscripts",
}


class Format(IdLabel):
    pass


EBooks = Format(id="v", label="E-books")
EJournals = Format(id="j", label="E-journals")
Books = Format(id="a", label="Books")
Journals = Format(id="d", label="Journals")
Audio = Format(id="i", label="Audio")
Pictures = Format(id="k", label="Pictures")
ArchivesAndManuscripts = Format(id="h", label="Archives and manuscripts")
ArchivesDigital = Format(id="hdig", label="Born-digital archives")
