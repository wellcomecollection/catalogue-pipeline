import numpy as np
from PIL import Image
from joblib import Parallel, delayed


class PaletteEncoder:
    """A class for embedding color information from an image."""

    def __init__(self, n_bins: int = 6, alpha: float = 5):
        """
        Initialise the encoder.

        Args:
            n_bins (int):
                Number of bins to use along each color dimension (r, g, b).
            alpha (float):
                Standard deviation of the noise added to each pixel.

        """
        self.n_bins = n_bins
        self.bins = np.linspace(0, 255, n_bins + 1)
        self.alpha = alpha
        self.delayed_process_image = delayed(self.process_image)

    def embed(self, pixel_array: np.ndarray) -> np.ndarray:
        """
        Embed color information from the given image.

        The embedding process involves resizing the image, converting it to RGB,
        and creating a color histogram. The histogram is composed of n_bins^3
        bins, where n_bins is the number of bins in each dimension (red, green,
        blue). Each pixel in the image is repeated 10 times and some noise is
        added to each pixel. This allows colours near the bin boundaries to fall
        into multiple bins, making the embedding more robust.

        Args:
            pixel_array: np.ndarray
                The flattened array of RGB pixels from the input image

        Returns:
            np.ndarray:
                The flattened color histogram as a 1D numpy array.

        """
        repeated_pixel_array = np.repeat(pixel_array, 10, axis=0)
        noise = np.random.normal(0, self.alpha, repeated_pixel_array.shape)
        pixel_array = repeated_pixel_array + noise

        histogram, _ = np.histogramdd(
            pixel_array, bins=[self.bins, self.bins, self.bins]
        )

        # make sure the vector is of unit length
        histogram = histogram / np.linalg.norm(histogram)

        return histogram.flatten()

    def average_color_hex(self, pixel_array: np.ndarray) -> np.ndarray:
        """
        Extract the average color of the input image and return it as a hex
        string.

        Args:
            pixel_array: np.ndarray
                The flattened array of RGB pixels from the input image

        Returns:
            np.ndarray:
                The flattened color histogram as a 1D numpy array.
        """
        average = pixel_array.mean(axis=0)
        r, g, b = average.astype(int)
        hex = "#{:02x}{:02x}{:02x}".format(r, g, b)
        return hex

    def process_image(self, image: Image):
        rgb_image = image.convert("RGB").resize(
            (50, 50),
            # resample using nearest neighbour to preserve the original colours.
            # using the default resample method (bicubic) will result in a
            # blurring/blending of colours
            resample=Image.NEAREST,
        )
        pixel_array = np.array(rgb_image).reshape(-1, 3)
        embedding = self.embed(pixel_array)

        return {
            # We want to truncate these bytes because dense_vector fields do
            # not support double precision floating point numbers
            # https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html#dense-vector-params
            # The inference manager receives base64-encoded bytes and converts them to
            # (single precision) floats.
            "palette_embedding": np.float32(embedding),
            "average_color_hex": self.average_color_hex(pixel_array),
        }

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
