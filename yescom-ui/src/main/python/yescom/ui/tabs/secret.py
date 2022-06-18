#!/usr/bin/env python3

from PyQt6.QtWidgets import *

from pyqtconsole.console import PythonConsole

from ez.pogdog.yescom import YesCom


class DebugTab(QWidget):
    """
    Secret debug tab :o.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = parent

        self._setup_tab()

    def _setup_tab(self) -> None:
        main_layout = QVBoxLayout(self)

        wtf_label = QLabel(self)
        wtf_label.setText("WTF secret debug tab.")
        main_layout.addWidget(wtf_label)

        self.console = PythonConsole(self)
        self.console.push_local_ns("yescom", self.yescom)
        self.console.push_local_ns("main_window", self.main_window)
        self.console.push_local_ns("close", self._close)
        self.console.eval_queued()
        # self.console.eval_in_thread()
        main_layout.addWidget(self.console)

    def _close(self) -> None:
        if hasattr(self.main_window, "debug_tab"):
            self.main_window.tab_widget.removeTab(self.main_window.tab_widget.indexOf(self))
            del self.main_window.debug_tab


from ..main import MainWindow
