#!/usr/bin/env python3

from PyQt6.QtWidgets import *


class OptionsTab(QWidget):
    """
    Allows you to set options for YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<OptionsTab() at %x>" % id(self)


from ..main import MainWindow
