#!/usr/bin/env python3

from PyQt5.QtWidgets import *


class OptionsTab(QTabWidget):
    """
    Allows you to set options for YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<OptionsTab() at %x>" % id(self)


from ..window import MainWindow
