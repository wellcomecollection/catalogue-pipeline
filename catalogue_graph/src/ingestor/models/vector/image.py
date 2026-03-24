from ingestor.models.augmented.image import AugmentedImage
from models.pipeline.serialisable import ElasticsearchModel


class ImageVectorValues(ElasticsearchModel):
    features: list[float]
    palette_embedding: list[float]

    @classmethod
    def from_augmented_image(cls, image: AugmentedImage) -> "ImageVectorValues":
        return ImageVectorValues(
            features=image.state.inferred_data.features,
            palette_embedding=image.state.inferred_data.palette_embedding,
        )
