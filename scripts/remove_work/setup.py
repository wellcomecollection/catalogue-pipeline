from setuptools import find_packages, setup

setup(
    name="Wellcome Collection catalogue scripts: remove_work",
    description="If you want to delete a work from the Catalogue API and prevent it reappearing",
    python_requires=">=3",
    install_requires=["boto3", "requests"],
)
