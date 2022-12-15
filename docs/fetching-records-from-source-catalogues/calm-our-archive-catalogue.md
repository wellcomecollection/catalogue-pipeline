# CALM: Our archive catalogue

CALM is the database used by Collections Information to manage our archives catalogue.

## Identifiers in CALM

A record in CALM has three identifiers:

*   A **RecordID**, which is a UUID used by the CALM database, e.g. `002c5acf-a977-4f1e-ae6f-bcf84143ec05`

*   A **RefNo**, which is a slash-separated string that tells us the position of a record in an archive hierachy, e.g. `PPMLV/C/7/6/6`.

    All the records in the same archive will have the same prefix before the first slash, e.g. everything with a RefNo that starts `PPMLV/` is part of the Dr Marthe Louise Vogt archive.

    PP is a common prefix that stands for "Personal Papers"; SA is another that stands for "Societies and Associations".

*   An **AltRefNo**, which is the display version of the RefNo, e.g. `PP/MLV/C/7/6/6`.

    This may be formatted slightly differently, and should not be analysed as a structured string.