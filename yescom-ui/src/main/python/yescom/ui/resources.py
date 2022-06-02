#!/usr/bin/env python3

import os
import shutil
import tempfile
from typing import Union
from zipfile import ZipFile

from PyQt5.QtGui import *

from ez.pogdog.yescom.api import Logging

logger = Logging.getLogger("yescom.ui.resources")


class Resources:
    """
    Manages resources present in the current jar file. FUCK QT!!!
    """

    INSTANCE: Union["Resources", None] = None

    def __init__(self, jar_path: str) -> None:
        self.__class__.INSTANCE = self

        self._jar_path = jar_path
        self._open_zip = ZipFile(self._jar_path)
        self._temp_dir = os.path.join(tempfile.gettempdir(), "yescom-resources")

        if not os.path.exists(self._temp_dir):
            os.makedirs(self._temp_dir)

        logger.finer("Jar path: " + repr(self._jar_path))
        logger.finer("Temp dir: " + repr(self._temp_dir))

    def extract(self, file: str) -> str:
        """
        Extracts a resource from the jar file into a temporary one.

        :param file: The name of the file to extract.
        :return: The path to the extracted temporary file.
        """

        temp_extract = os.path.join(self._temp_dir, os.path.split(file)[1])
        logger.finest("Extracting resource %s -> %s." % (file, temp_extract))

        with open(temp_extract, "wb") as fileobj:
            fileobj.write(self._open_zip.read(file))

        return temp_extract

    def pixmap(self, file: str) -> QPixmap:
        """
        Loads a pixmap from a resource.

        :param file: The name of the file to load.
        :return: The loaded pixmap.
        """

        pixmap = QPixmap()
        pixmap.loadFromData(self._open_zip.read(file))
        return pixmap

    def release(self) -> None:
        logger.fine("Closing resources...")
        self._open_zip.close()
        shutil.rmtree(self._temp_dir)
