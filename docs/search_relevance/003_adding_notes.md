# Test 3: Adding notes

## Candidates

Two candidates were compared, one control, and one introducing the newly indexed notes to the list of queried fields. These fields are inconsistently filled out and their lengths can vary wildly, so we'd like to check that their inclusion doesn't dramatically affect the documents' IDF and therefore the overall goodness of search.

**without notes**

Current default, searching only the title, description, subject, genre, identifiers.

**with notes**

Notes fields are also queried alongside the default fields, with no extra boosting

## Results

As mentioned in [#3933](https://github.com/wellcometrust/platform/issues/3933), we saw no discernable difference between the two candidates.
![image](https://user-images.githubusercontent.com/11006680/67676260-0151ae00-f979-11e9-9027-167bef011f64.png)

## Conclusions

The reason we saw no difference is that the test never really ran as a result of a [bug](https://github.com/wellcometrust/platform/issues/4019) in the query selection. Decided to delay re-testing in favour of changes that we suspect will be more impactful.
