from typing import Optional

from models.pipeline.location import Location

from .id_label import DisplayIdLabel

LICENSE_LABEL_MAPPING = {
    "cc-by": "Attribution 4.0 International (CC BY 4.0)",
    "cc-by-nc": "Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)",
    "cc-by-nc-nd": "Attribution-NonCommercial-NoDerivatives 4.0 International (CC BY-NC-ND 4.0)",
    "cc-0": "CC0 1.0 Universal",
    "pdm": "Public Domain Mark",
    "cc-by-nd": "Attribution-NoDerivatives 4.0 International (CC BY-ND 4.0)",
    "cc-by-sa": "Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)",
    "cc-by-nc-sa": "Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)",
    "ogl": "Open Government Licence",
    "opl": "Open Parliament Licence",
    "inc": "In copyright",
}


LICENSE_URL_MAPPING = {
    "cc-by": "http://creativecommons.org/licenses/by/4.0/",
    "cc-by-nc": "https://creativecommons.org/licenses/by-nc/4.0/",
    "cc-by-nc-nd": "https://creativecommons.org/licenses/by-nc-nd/4.0/",
    "cc-0": "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
    "pdm": "https://creativecommons.org/share-your-work/public-domain/pdm/",
    "cc-by-nd": "https://creativecommons.org/licenses/by-nd/4.0/",
    "cc-by-sa": "https://creativecommons.org/licenses/by-sa/4.0/",
    "cc-by-nc-sa": "https://creativecommons.org/licenses/by-nc-sa/4.0/",
    "ogl": "http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/",
    "opl": "https://www.parliament.uk/site-information/copyright-parliament/open-parliament-licence/",
    "inc": "http://rightsstatements.org/vocab/InC/1.0/",
}


class DisplayLicense(DisplayIdLabel):
    url: str
    type: str = "License"

    @staticmethod
    def from_location(location: Location) -> Optional["DisplayLicense"]:
        if location.license is None:
            return None

        return DisplayLicense(
            id=location.license.id,
            label=LICENSE_LABEL_MAPPING[location.license.id],
            url=LICENSE_URL_MAPPING[location.license.id],
        )
