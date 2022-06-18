#!/usr/bin/env python3

from setuptools import setup, find_packages

from Cython.Build import cythonize

setup(
    name="yescom-ui",
    version="1.0",
    author="Edward E Stamper",
    description="😈",
    install_requires=[
        "bresenham",
        "PyQt6",
        "requests",
        "numpy",
        "jep",
    ],
    packages=find_packages(
        where="src/main/python",
        include=["*"],
    ),
    package_dir={
        "": "src/main/python",
    },
    # ext_modules=cythonize(
    #     "src/main/python/yescom/ui/renderer/biome/biomes.pyx",
    #     "src/main/python/yescom/ui/renderer/biome/rand.pyx",  # FIXME: Automate this
    # ),
)