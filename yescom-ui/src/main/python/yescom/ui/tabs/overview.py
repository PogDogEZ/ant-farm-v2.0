#!/usr/bin/env python3

from PyQt5.QtWidgets import *


class OverviewTab(QTabWidget):
    """
    Provides an overview of the server's information.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<OverviewTab() at %x>" % id(self)


from ..window import MainWindow
