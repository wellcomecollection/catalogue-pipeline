package weco.catalogue.internal_model.fixtures.index

import weco.catalogue.internal_model.matchers.EventuallyInElasticsearch

trait IndexFixtures extends WorksIndexFixtures with ImagesIndexFixtures with EventuallyInElasticsearch {

}
