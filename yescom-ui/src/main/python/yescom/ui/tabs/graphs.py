#!/usr/bin/env python3

from PyQt6.QtWidgets import *



class GraphsTab(QWidget):
    """
    Shows cool looking graphs detailing stuff like the tickrate, etc.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<GraphsTab() at %x>" % id(self)


from ..main import MainWindow
