#!/usr/bin/env python3

from PyQt5.QtWidgets import *


class GridViewTab(QWidget):
    """
    A tab that shows a grid view of loaded chunks and tracked players.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<GridViewTab() at %x>" % id(self)


from ..main import MainWindow
