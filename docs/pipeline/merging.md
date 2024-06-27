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

# Feature: The Matcher/Merger

## Scenario: A single work with nothing linked to it

```gherkin
Given A single work 
When the work is processed by the matcher/merger 
Then the work is returned
```

## Scenario: One Sierra and multiple Miro works are matched

```gherkin
Given a Sierra work and 3 Miro works 
When the works are processed by the matcher/merger 
Then the Miro works are redirected to the Sierra work 
	And images are created from the Miro works 
	And the merged Sierra work's images contain all of the images
```

## Scenario: One Sierra and one Miro work are matched

```gherkin
Given a Sierra work and a Miro work 
When the works are processed by the matcher/merger 
Then the Miro work is redirected to the Sierra work 
	And an image is created from the Miro work 
	And the merged Sierra work contains the image
```

## Scenario: One Sierra and one Ebsco work are matched

```gherkin
Given a Sierra work and a Miro work 
When the works are processed by the matcher/merger 
Then the Sierra work is redirected to the Ebsco work 
	And the Ebsco work should be unmodified
```

## Scenario: A Sierra picture and METS work are matched

```gherkin
Given a Sierra picture and a METS work 
When the works are processed by the matcher/merger 
Then the METS work is redirected to the Sierra work 
	And an image is created from the METS work 
	And the merged Sierra work contains no images 
	And the merged Sierra work contains the locations from both works
```

## Scenario: A Sierra ephemera work and METS work are matched

```gherkin
Given a Sierra ephemera work and a METS work 
When the works are processed by the matcher/merger 
Then the METS work is redirected to the Sierra work 
	And an image is created from the METS work 
	And the merged Sierra work contains the image 
	And the merged Sierra work contains the locations from both works
```

## Scenario: An AIDS poster Sierra picture, a METS and a Miro are matched

```gherkin
Given a Sierra picture with digcode `digaids`, a METS work and a Miro work 
When the works are processed by the matcher/merger 
Then the METS work and the Miro work are redirected to the Sierra work 
	And the Sierra work contains no images 
	And the merged Sierra work contains the locations from both works
```

## Scenario: A physical and a digital Sierra work are matched

```gherkin
Given a pair of a physical Sierra work and a digital Sierra work 
When the works are processed by the matcher/merger 
Then the digital work is redirected to the physical work 
	And the physical work contains the digitised work's identifiers
```

## Scenario: Audiovisual Sierra works are not merged

```gherkin
Given a physical Sierra AV work and its digitised counterpart 
When the works are processed by the matcher/merger 
Then both original works remain visible
```

## Scenario: A Calm work and a Sierra work are matched

```gherkin
Given a Sierra work and a Calm work 
When the works are processed by the matcher/merger 
Then the Sierra work is redirected to the Calm work 
	And the Calm work contains the Sierra item ID
```

## Scenario: A Calm work, a Sierra work, and a Miro work are matched

```gherkin
Given A Calm work, a Sierra work and a Miro work 
When the works are processed by the matcher/merger 
Then the Sierra work is redirected to the Calm work 
	And the Miro work is redirected to the Calm work 
	And the Calm work contains the Miro location 
	And the Calm work contains the Miro image
```

## Scenario: A Calm work, a Sierra picture work, and a METS work are matched

```gherkin
Given A Calm work, a Sierra picture work and a METS work 
When the works are processed by the matcher/merger 
Then the Sierra work is redirected to the Calm work 
	And the METS work is redirected to the Calm work 
	And the Calm work contains the METS location 
	And the Calm work contains the METS image
```

## Scenario: A digitised video with Sierra physical records and e-bibs

```gherkin
Given a Sierra physical record, an e-bib, and a METS work 
When the works are processed by the matcher/merger 
Then the METS work is redirected to the Sierra e-bib 
	And the Sierra e-bib gets the items from the METS work 
	And the Sierra physical work is unaffected
```

## Scenario: A Tei and a Sierra digital and a sierra physical work are merged

```gherkin
Given a Tei, a Sierra physical record and a Sierra digital record 
When the works are processed by the matcher/merger 
Then the Sierra works are redirected to the tei 
	And the tei work has the Sierra works' items 
	And the tei work has the Sierra works' identifiers
```

## Scenario: A Tei with internal works and a Sierra digital and a sierra physical work are merged

```gherkin
Given a Tei, a Sierra physical record and a Sierra digital record 
When the works are processed by the matcher/merger 
Then the Sierra works are redirected to the tei 
	And the tei work has the Sierra works' items 
	And the tei work has the Sierra works' identifiers 
	And the internal tei works are returned 
	And the tei internal works contain the sierra item 
	And the tei internal works retain their collectionsPath
```

## Scenario: A Tei work passes through unchanged

```gherkin
Given a Tei 
When the tei work is merged 
Then the tei work should be a TEI work 
	And the the tei inner works should be returned
```

## Scenario: CollectionPath is prepended to internal tei works if the work is not merged

```gherkin
Given a Tei 
When the work is processed by the matcher/merger 
Then the tei work should be a TEI work
```

## Scenario: A TEI work, a Calm work, a Sierra work and a METS work

```gherkin
Given four works 
When the works are processed by the matcher/merger 
Then Everything should be redirected to the TEI work 
	And the TEI work gets all the CALM and Sierra identifiers 
	And it has no METS identifier 
	And it only has two items (one physical, one digital) 
	And it gets the METS thumbnail
```

## Scenario: Miro, Calm and Sierra but the Miro is deleted

```gherkin
Given the works 
When the works are processed by the matcher/merger 
Then the Sierra work is redirected to the Calm work
```

## Scenario: Miro, Calm and Sierra but the Miro is missing

```gherkin
Given the works 
When the works are processed by the matcher/merger 
Then the Sierra work is redirected to the Calm work
```

