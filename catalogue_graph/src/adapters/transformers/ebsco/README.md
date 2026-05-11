# Transforming MARC

This package governs the transformation of MARC records
into the Wellcome internal model.

## Principles

### Validation

Thorough validation of input is not a concern of the transformer.
The two essential constraints are that the input is:

* readable (i.e. well-formed XML, understandable as MARC)
* provides the basic fields we require (id and title) for each record

There is no need to apply further validation such as ensuring
NR (non-repeating) fields and subfields occur only once.

### Composable/Reusable

Although originally written specifically for data received from EBSCO,
the intent is that these should be able to transform any MARC content.

Anything designed to handle the vagaries of a particular provider should
be handled by composing the overall transformer with a separate field-level
transformer, rather than overcomplicating the common field-level transformers.