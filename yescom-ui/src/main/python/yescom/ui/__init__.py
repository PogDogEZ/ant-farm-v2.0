#!/usr/bin/env python3

import sys

from PyQt5.QtCore import *
from PyQt5.QtWidgets import *

from .window import MainWindow

from ez.pogdog.yescom.api import Logging


def main() -> None:
    logger = Logging.getLogger("yescom.ui")
    logger.info("Python UI component loaded.")

    preferred_style = "Fusion"
    logger.fine("Current styles: %r." % QStyleFactory.keys())

    if not preferred_style in QStyleFactory.keys():
        preferred_style = QStyleFactory.keys()[0]

    app = QApplication(sys.argv)
    app.setStyle(preferred_style)

    main_window = MainWindow()
    main_window.show()

    app.exec()
