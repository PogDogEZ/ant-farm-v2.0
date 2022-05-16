#!/usr/bin/env python3

from PyQt5.QtWidgets import *


class AccountsTab(QTabWidget):
    """
    Allows you to manage accounts that are associated with YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

    def __repr__(self) -> str:
        return "<AccountsTab() at %x>" % id(self)


from ..window import MainWindow
