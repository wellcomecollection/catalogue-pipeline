# Creating canonical identifiers

Our source catalogues all use their own identifier schemes, which may have differently formatted or overlapping values.

For example, the identifier `b14561980` could refer to:

*   a bib record for a book in Sierra, or
*   the METS file for a digitised copy of that book

To help us to refer to entities consistently and unambiguously, the catalogue pipeline creates its own "canonical" identifiers. These are distinct from identifiers in the source catalogue, and used whenever we need to refer to specific records, e.g. in URLs.

## The format of canonical IDs

Canonical IDs are chosen for the following properties:

* They should be **short**, ideally something that fits on a single post-it note
* They should be **unambiguous**, so they don't use characters which can look ambiguous (e.g. letter `O` and numeral `0`)
* They should be **URL safe**

## How we create canonical IDs from source identifiers

There's a one-to-one relationship between canonical IDs and source identifiers.

A source identifier is made of three parts:

* The **source system** of the identifier, e.g. `miro-image-number` or `sierra-system-number`. This helps us distinguish between overlapping identifiers from different systems, like the `b14561980` example above.

* The **value** of the identifier, e.g. `b14561980` or `V0000287`.

* The **ontology type** of the identifier. This helps us create different canonical IDs for different contexts. e.g. Miro identifiers are used for both a Work and an Image, and we want the Work and the Image to have different canonical IDs. The Miro source identifiers will have ontology types `Work` and `Image`, respectively, so they get distinct canonical IDs.
