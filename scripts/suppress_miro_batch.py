#!/usr/bin/env python3
from miro_updates import suppress_image, update_miro_image_suppressions_doc

def suppress_miro(miro_id):
    message = "Brought to Life/Science Museum images. Take-down request signed off by Wellcome Collection quarterly governance meeting, 24 August 2023"
    try:
        suppress_image(miro_id=miro_id, message=message)
    except:
        with open('suppress_miro_errors.txt', 'a') as error_file:
            error_file.write(f"{miro_id}\n")
            continue

if __name__ == "__main__":
    with open("btl_miro_images_to_suppress.txt") as btl_miro_images_to_suppress:
       for id in btl_miro_images_to_suppress:
            suppress_miro(id)

    update_miro_image_suppressions_doc()

