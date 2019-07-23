# catalogue

[![Build Status](https://travis-ci.org/wellcometrust/catalogue.svg?branch=master)](https://travis-ci.org/wellcometrust/catalogue)

The Catalogue API allows you to search our museum and library collections.

The catalogue consits of three main parts with supporting services. These are:
* [Sierra adapter](./sierra_adapter/README.md): Allowing us to store data from Sierra that is retrievable performantly and at
  scale.
* [Pipeline](./pipeline/README.md): Taking adapter data and putting it into our query index, Elasticsearch
* [API](./api/README.md): Serving the data from Elastic search to clients via HTTP 

---

Documentation for using our API can be found at <https://developers.wellcomecollection.org/catalogue>,
and the API itself is published at <https://api.wellcomecollection.org/catalogue>.

---

This is part of the [Wellcome Digital Platform](https://github.com/wellcometrust/platform).
