#!/usr/bin/env python3

import os
import threading
import tempfile
from zipfile import ZipFile
from typing import List

from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from .main import MainWindow
from .resources import Resources
from .. import emitters  # Localise emitters

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging

logger = Logging.getLogger("yescom.ui")


class SplashScreen(QSplashScreen):
    """
    https://stackoverflow.com/questions/22423781/using-a-gif-in-splash-screen-in-pyqt
    """

    def __init__(self, width: int, height: int, temp_extract: str) -> None:
        QSplashScreen.__init__(self)

        self.width = width
        self.height = height

        self.setWindowFlag(Qt.WindowStaysOnTopHint)

        self.movie = QMovie(temp_extract)
        self.movie.frameChanged.connect(self._on_frame_changed)
        self.movie.start()

    def _on_frame_changed(self, number: int) -> None:
        pixmap = self.movie.currentPixmap().scaled(self.width, self.height)
        self.setPixmap(pixmap)
        self.setMask(pixmap.mask())


def _initialise(args: List[str], init_loop: QEventLoop) -> None:
    logger.info("Starting YesCom core...")
    YesCom.main(args)

    YesCom.getInstance().start()

    while not YesCom.getInstance().isInitialised():
        ...

    init_loop.exit(0)


def main(args: List[str], jar_path: str) -> None:
    logger.info("Python UI component loaded.")

    resources = Resources(jar_path)
    splash_gif = resources.extract("yescom_splash.gif")  # FUCK QT

    preferred_style = "Fusion"
    logger.fine("Current styles: %r." % QStyleFactory.keys())

    if not preferred_style in QStyleFactory.keys():
        preferred_style = QStyleFactory.keys()[0]

    app = QApplication(list(args))
    app.setStyle(preferred_style)

    screen = QApplication.desktop().screen()

    splash_screen = SplashScreen(int(screen.width() / 2), int(screen.height() / 2), splash_gif)
    splash_screen.show()
    app.processEvents()

    init_loop = QEventLoop()
    threading.Thread(target=_initialise, args=(args, init_loop)).start()
    init_loop.exec()

    splash_screen.movie.stop()
    os.remove(splash_gif)  # Clean up early

    main_window = MainWindow()
    main_window.resize(int(screen.width() / 2), int(screen.height() / 2))
    main_window.move(screen.rect().center() - main_window.rect().center())
    main_window.setWindowState(main_window.windowState() & ~Qt.WindowMinimized | Qt.WindowActive)
    main_window.activateWindow()
    main_window.show()
    splash_screen.finish(main_window)

    app.exec()
