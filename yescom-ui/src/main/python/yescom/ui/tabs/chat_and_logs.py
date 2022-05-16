#!/usr/bin/env python3

from PyQt5.QtWidgets import *


class ChatAndLogsTab(QTabWidget):
    """
    Shows either the server chat or YesCom logs.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<ChatAndLogsTab() at %x>" % id(self)


from ..window import MainWindow
