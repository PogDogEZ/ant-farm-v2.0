#!/usr/bin/env python3

from PyQt5.QtWidgets import *


class TasksTab(QTabWidget):
    """
    Allows the user to manage tasks running on YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<TasksTab() at %x>" % id(self)


from ..window import MainWindow
