#!/usr/bin/env python3

from PyQt5.QtWidgets import *

from ez.pogdog.yescom import YesCom


class DebugTab(QWidget):
    """
    Secret debug tab :o.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = parent


from ..main import MainWindow
