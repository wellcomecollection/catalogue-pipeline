from setuptools import setup

setup(
    name="catalogue_python_fixtures",
    description="Pytest fixtures used across catalogue apps",
    packages=["fixtures"],
    entry_points={"pytest11": ["catalogue_aws_fixtures = fixtures.aws"]},
    install_requires=["pytest", "moto"],
)
