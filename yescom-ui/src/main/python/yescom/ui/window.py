#!/usr/bin/env python3

from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from .. import emitters

from java.lang import System

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging

logger = Logging.getLogger("yescom.ui.window")


class MainWindow(QMainWindow):
    """
    The main YesCom window, duh.
    """

    yescom = YesCom.getInstance()

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)

        self.setWindowTitle("YesCom \ud83d\ude08")

        self.event_thread = MainWindow.EventQueueThread(self)
        self.event_thread.start()

        self.central_widget = QWidget(self)
        main_layout = QVBoxLayout(self.central_widget)

        self._setup_top_bar(main_layout)
        self._setup_tabs(main_layout)

        self.setCentralWidget(self.central_widget)

    def __repr__(self) -> str:
        return "<MainWindow() at %x>" % id(self)

    def closeEvent(self, event: QCloseEvent) -> None:
        logger.finer("Received close event.")
        System.exit(0)  # self.yescom.shutdown()

        super().closeEvent(event)

    def _setup_top_bar(self, main_layout: QVBoxLayout) -> None:
        layout = QHBoxLayout()

        self.smiling_imp_label = QLabel(self.central_widget)
        self.smiling_imp_label.setText("\ud83d\ude08")
        layout.addWidget(self.smiling_imp_label)

        layout.addItem(QSpacerItem(40, 20, QSizePolicy.Expanding, QSizePolicy.Minimum))

        self.servers_box = QComboBox(self.central_widget)
        layout.addWidget(self.servers_box)

        layout.setStretch(1, 3)
        layout.setStretch(2, 1)

        main_layout.addLayout(layout)

    def _setup_tabs(self, main_layout: QVBoxLayout) -> None:
        self.tab_widget = QTabWidget(self.central_widget)

        self.overview_tab = OverviewTab(self)
        self.tab_widget.addTab(self.overview_tab, "Overview")

        self.grid_view_tab = GridViewTab(self)
        self.tab_widget.addTab(self.grid_view_tab, "Grid View")

        self.accounts_tab = AccountsTab(self)
        self.tab_widget.addTab(self.accounts_tab, "Accounts")

        self.tasks_tab = TasksTab(self)
        self.tab_widget.addTab(self.tasks_tab, "Tasks")

        self.graphs_tab = GraphsTab(self)
        self.tab_widget.addTab(self.graphs_tab, "Graphs")

        self.chat_and_logs_tab = ChatAndLogsTab(self)
        self.tab_widget.addTab(self.chat_and_logs_tab, "Chat / Logs")

        self.options_tab = OptionsTab(self)
        self.tab_widget.addTab(self.options_tab, "Options")

        main_layout.addWidget(self.tab_widget)

    # ------------------------------ Classes ------------------------------ #

    class EventQueueThread(QThread):
        """
        Due to limitations, calls to interpreters can only be made through the same Java thread that created the
        interpreter, so events need to be accessed in a queued fashion.
        """

        yescom = YesCom.getInstance()

        def __init__(self, parent: "MainWindow") -> None:
            super().__init__(parent)

        def run(self) -> None:
            while self.yescom.isRunning():
                for emitter in emitters.EMITTERS:
                    if not emitter.queuedEvents.isEmpty():
                        with emitter.synchronized():  # FIXME: Emitter::flush() not working??
                            while not emitter.queuedEvents.isEmpty():
                                object_ = emitter.queuedEvents.remove()
                                for listener in emitter.pyListeners:
                                    listener(object_)

                QThread.msleep(50)  # Damn


from .tabs.accounts import AccountsTab
from .tabs.chat_and_logs import ChatAndLogsTab
from .tabs.graphs import GraphsTab
from .tabs.grid_view import GridViewTab
from .tabs.options import OptionsTab
from .tabs.overview import OverviewTab
from .tabs.tasks import TasksTab
