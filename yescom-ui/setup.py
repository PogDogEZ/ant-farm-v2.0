#!/usr/bin/env python3

from setuptools import setup, find_packages

from Cython.Build import cythonize

setup(
    name="yescom-ui",
    version="1.0",
    author="Edward E Stamper",
    description="😈",
    install_requires=[  # TODO: Install requirements
        "PyQt5",
    ],
    packages=find_packages(
        where="src/main/python",
        include=["*"],
    ),
    package_dir={
        "": "src/main/python",
    },
    # ext_modules=cythonize(
    #     "src/main/python/yescom/ui/renderer/biome.pyx",  # FIXME: Automate this
    # ),
)