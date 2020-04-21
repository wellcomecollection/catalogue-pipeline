# Merging

Merging works is split up into 2 steps:

## 1. Linking works

Using features from the source data to calculate which works are linked.

e.g:
* the `BNumber` from a Calm record
* Marcfield `776$w` linking a Sierra record to it's digitised counterpart

This is carried out by the transformers of the respective sources data.

### How are works currently linked?

![How works are currently linked](../.gitbook/assets/merger_linked_works.png)

[View on Excalidraw][linked-works-diagram]

## 2. Merging linked works

Merging works consist of a few steps

* Choose a field to be merged e.g. items
* Choose a target - the work that we will merge into.
  e.g. Works from Calm
* Choose the sources - works that will be merged into the target.
  e.g. Sierra works with a single item

Below are diagrams of the merging rules

### Calm items

![How we merge items into calm](../.gitbook/assets/merger_items_into_calm_target.png)

[View on Excalidraw][merging-items-into-calm-target]

[linked-works-diagram]: https://excalidraw.com/#json=6218082506768384,6y0SZ5U20AFChDSaScsxww  "How we link works"
[merging-items-into-calm-target]: https://excalidraw.com/#json=5992885962932224,PmNI_PpnKnm0slrY_MJ0jg  "How we merge items into calm target"
