import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed


class PaletteEncoder:
    def __init__(self, palette_size, precision_levels):
        self.palette_size = palette_size
        self.precision_levels = precision_levels
        self.delayed_process_image = delayed(self.process_image)

    def get_significant_colours(self, image, p=0.5):
        """
        extract n significant colours from the image pixels by taking the
        centres of n kmeans clusters of the image's pixels arranged in colour
        space.
        The biggest cluster is counted n^p times, the second largest (n-1)^p
        times, ..., and the smallest cluster counted once.
        """
        pixels = np.array(image).reshape(-1, 3)
        clusterer = KMeans(n_clusters=self.palette_size).fit(pixels)
        colours = clusterer.cluster_centers_[::-1]
        significant_colours = []
        for val, colour in enumerate(colours):
            significant_colours.extend([colour] * int(val ** p))

        return np.stack(significant_colours)

    def get_colour_histogram(self, colours, precision):
        """
        get the bins in which the images' significant colours are found at a
        specified level of precision
        """
        bins = np.linspace(0, 256, precision + 1)
        histogram, _ = np.histogramdd(sample=colours, bins=[bins, bins, bins])
        bin_counts = histogram.reshape(-1)
        return bin_counts

    def encode_for_elasticsearch(self, flat_values):
        """
        turn a list of bin counts into a list of strings for elasticsearch
        """
        encoded_list = []
        for index, value in enumerate(flat_values):
            encoded_list.extend([str(index)] * int(value))
        return encoded_list

    def process_image(self, image):
        """
        extract presence of colour in the image at multiple precision levels
        """
        colours = self.get_significant_colours(image)
        combined_results = np.array(
            [
                bin_count
                for precision in self.precision_levels
                for bin_count in self.get_colour_histogram(
                    colours=colours, precision=precision
                )
            ]
        )

        return self.encode_for_elasticsearch(combined_results)

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
