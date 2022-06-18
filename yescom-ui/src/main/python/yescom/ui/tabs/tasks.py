#!/usr/bin/env python3

from PyQt6.QtWidgets import *

from ez.pogdog.yescom.api import Logging

logger = Logging.getLogger("yescom.ui.tabs.tasks")


class TasksTab(QWidget):
    """
    Allows the user to manage tasks running on YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self._setup_tab()

    def __repr__(self) -> str:
        return "<TasksTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        logger.finer("Setting up tasks tab...")

        main_layout = QHBoxLayout(self)


from ..main import MainWindow
