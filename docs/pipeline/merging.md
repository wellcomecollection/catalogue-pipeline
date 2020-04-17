# Merging

## The process
Merging works is split up into 2 steps:

### 1. Linking works

Using features from the source data to calculate which works are linked.

e.g:
* `BNumber` from a Calm record
* Marcfield `776$w` linking a Sierra record to it's digitised counterpart
* Marcfield `962$u` or `089$a` linking Miro works to Sierra works
* `recordIdentifier` from METS works to Sierra works

This is carried out by the transformers of the respective sources data.

#### How are works currently linked?
![How works are currently linked](../.gitbook/merger_linking_works.png)
https://excalidraw.com/#json=5964037271584768,ojtCECzrMrgSJuBp3VCvHw


### 2. Merging linked works

## Finding the target

The `target` in the merging process is the work
* which all data will be merged into
* the work merged sources will redirect to

We select a `target` in a waterfall fashion
* Calm
* Sierra work with a physical location on an item
* A Sierra work

If a target isn't selected, all linked works will left unmerged.
