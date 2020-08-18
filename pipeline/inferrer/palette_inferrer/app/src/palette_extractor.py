import numpy as np
from skimage.color import rgb2lab
from .logging import get_logger

logger = get_logger(__name__)


class PaletteExtractor:
    def __init__(self):
        pass

    def encode_for_elasticsearch(self, flat_values):
        encoded_list = []
        for index, value in enumerate(flat_values):
            encoded_list.extend([str(index)] * value)

        return encoded_list

    def get_colour_histogram(self, lab_pixels, n_bins):
        """
        bins image pixels into n^3 chunks of perceptually-even colour space at 
        a specified level of precision, and normalises the relative intensity 
        of colour in each chunk
        """
        histogram, _ = np.histogramdd(
            sample=lab_pixels,
            bins=(n_bins, n_bins, n_bins)
        )

        normalised_histogram = (
            histogram * 10 /
            histogram.max()
        ).astype(int)

        flat_values = normalised_histogram.reshape(-1)
        return flat_values

    def __call__(self, image, precision_levels=[2, 4, 8]):
        """
        extract presence of colour in the image at multiple precision levels
        """
        lab_pixels = rgb2lab(image).reshape(-1, 3)
        combined_results = [
            bin_count
            for precision_level in precision_levels
            for bin_count in self.get_colour_histogram(
                lab_pixels=lab_pixels,
                n_bins=precision_level
            )
        ]

        return self.encode_for_elasticsearch(combined_results)
