# Miro: Our image collections

Miro is the name of the asset management software used for Wellcome Images, an online picture service that [Wellcome used to run](https://github.com/wellcomecollection/wellcomeimages.org).
This service included both openly-licenced images and images that required a paid licence.

Although wellcomeimages.org has now been subsumed by the image search on wellcomecollection.org, we still have the images and metadata, and we call it the "Miro" data.

Not all the wellcomeimages.org data came to the new site; only the subset which could be made available under an open licence.

## Identifiers in Miro

A record in Miro may have (up to) three parts:

*   A prefix letter, which identifies the broad collection.
    From a [Slack thread in 2020](https://wellcome.slack.com/archives/CGXDT2GSH/p1591876702147000?thread_ts=1591870071.144200&cid=CGXDT2GSH):

    > A – Animal Images
    > AS – Family Life. Child Development
    > B – Biomedical supplied by external contributors
    > C – Corporate images relating to the Wellcome Trust
    > D – Footage
    > F – Microfilm
    > FP – Family life child development
    > L – Images of items held in the Library. Historical.
    > M – Images of items held in the library. Historical.
    > N – Clinical
    > S – Slide collection
    > V – Iconographic/works of art
    > W – Publishing group International Health

*   An image number, which identifies this image within the collection.
    This is usually seven digits, e.g. `0029572`.

*   Suffix characters, e.g. `ER` and `EL` are used to identify the right- and left-hand images of the same page in a book.
    This is optional and doesn't appear on all Miro records.

Examples of Miro identifiers: `V0029572`, `V0018563ER`.

## The Miro data

We have XML exports of the Miro data from before Wellcome Images was turned off; these are kept in the [storage service].
We have a JSON copy of this data in the platform account.

When we got the Miro data, we sorted the images into three buckets:

*   Open access – anything where we had positive confirmation from the original contributor that we could keep using their image, and make it available under a permissive licence
*   Staff access – anything we wanted to keep but couldn't put on the public website (this includes a lot of our in-house photography)
*   Cold store – anything we didn't want to or weren't sure if we could keep

Only the open access images are available on the website and to the catalogue pipeline; we don't use the other images.

[storage service]: https://github.com/wellcomecollection/storage-service

## How we update records

It is extremely rare for us to update Miro data; usually the Collections team are responsible for keeping data up-to-date, and there's no easy way for them to edit the Miro data exports.

We can override a select number of fields, including the "licence" field, but we try to do so as little as possible.
All the Miro images we're keeping are gradually being ingested through Goobi, after which they'll have a METS file and an editable Sierra record -- and this will replace the legacy Miro record.

## How we delete records

Occasionally Collections will ask us to take down a Miro image; we have a script for doing this in the pipeline repo.

All the Miro takedowns are recorded here: https://github.com/wellcomecollection/private/blob/main/miro-suppressions.md
