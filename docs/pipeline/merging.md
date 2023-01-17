# Merging

Merging works occurs in 2 steps:

## 1. Linking works

Using features from the source data to calculate which works are linked.

e.g:

* the `BNumber` from a Calm record
* Marcfield `776$w` linking a Sierra record to it's digitised counterpart

This is carried out by the transformers of the respective sources data.

### How are works currently linked?

![How works are currently linked](../.gitbook/assets/merger_linked_works%20%281%29%20%281%29.png)

[View on Excalidraw](https://excalidraw.com/#json=6218082506768384,6y0SZ5U20AFChDSaScsxww)

## 2. Merging linked works

Merging works consist of a few steps

* Choose a field to be merged e.g. items
* Choose a target - the work that we will merge into.

  e.g. Works from Calm

* Choose the sources - works that will be merged into the target.

  e.g. Sierra works with a single item

Below are diagrams of the merging rules

### Items

#### Calm

![How we merge items into calm](../.gitbook/assets/merger_items_into_calm_target%20%281%29.png)

[View on Excalidraw](https://excalidraw.com/#json=5992885962932224,PmNI_PpnKnm0slrY_MJ0jg)

#### Sierra single item

![How we merge items into sierra single item](../.gitbook/assets/merger_items_into_sierra_single_item_target%20%281%29%20%281%29.png)

[View on Excalidraw](https://excalidraw.com/#json=4647141444157440,xNIaaYYJpX1p6HG6zSMDHQ)

#### Sierra multi item

![How we merge items into sierra multi item](../.gitbook/assets/merger_items_into_sierra_multi_item_target.png)

[View on Excalidraw](https://excalidraw.com/#json=4842487856234496,rOhr0AgAV_O0i6lwx0bWKQ)

