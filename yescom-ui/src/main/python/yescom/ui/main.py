#!/usr/bin/env python3

import time
from typing import Union

from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from .. import emitters

from java.lang import System

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.core.connection import Server

logger = Logging.getLogger("yescom.ui.window")


class MainWindow(QMainWindow):
    """
    The main YesCom window, duh.
    """

    INSTANCE: Union["MainWindow", None] = None

    # ------------------------------ Signals ------------------------------ #

    tick = pyqtSignal()

    server_changed = pyqtSignal()
    connection_established = pyqtSignal(object)
    connection_lost = pyqtSignal(object)

    account_added = pyqtSignal(object)
    account_error = pyqtSignal(object)

    player_added = pyqtSignal(object)
    player_removed = pyqtSignal(object)

    player_login = pyqtSignal(object)
    player_logout = pyqtSignal(object)

    player_position_update = pyqtSignal(object)
    player_health_update = pyqtSignal(object)
    player_server_stats_update = pyqtSignal(object)

    player_joined = pyqtSignal(object)
    player_left = pyqtSignal(object)
    player_gamemode_update = pyqtSignal(object)
    player_ping_update = pyqtSignal(object)

    # ------------------------------ Properties ------------------------------ #

    @property
    def current_server(self) -> Server:
        """
        :return: The server the user is currently viewing.
        """

        return self._current_server

    @current_server.setter
    def current_server(self, value: Server) -> None:
        if value != self._current_server:
            self._current_server = value
            self.server_changed.emit()

    def __init__(self, *args, **kwargs) -> None:
        if self.__class__.INSTANCE is not None:
            raise Exception("Duplicate initialisation of MainWindow.")
        self.__class__.INSTANCE = self

        super().__init__(*args, **kwargs)

        self.yescom = YesCom.getInstance()

        self.setWindowTitle("YesCom \ud83d\ude08")

        logger.fine("Starting event queue thread...")
        self.event_thread = EventQueueThread(self)
        self.event_thread.start()
        logger.fine("Starting skin downloader thread...")
        self.skin_downloader_thread = SkinDownloaderThread(self)
        self.skin_downloader_thread.start()

        self._current_server: Union[Server, None] = None

        self.central_widget = QWidget(self)
        main_layout = QVBoxLayout(self.central_widget)

        self._setup_signals()
        self._setup_top_bar(main_layout)
        self._setup_tabs(main_layout)

        self.setCentralWidget(self.central_widget)

    def __repr__(self) -> str:
        return "<MainWindow() at %x>" % id(self)

    def closeEvent(self, event: QCloseEvent) -> None:
        logger.finer("Received close event.")

        connected_servers = 0
        connected_players = 0
        for server in self.yescom.servers:
            if server.isConnected():
                connected_servers += 1
                for player in server.getPlayers():
                    if player.isConnected():
                        connected_players += 1

        if connected_servers or connected_players:
            message_box = QMessageBox(self)
            message_box.setIcon(QMessageBox.Question)
            message_box.setWindowTitle("Exit")
            message_box.setText("Are you sure you wish to exit YesCom?")
            # TODO: Trackers, tasks, etc...
            message_box.setInformativeText("There are currently %i connected player(s) over %i servers." %
                                           (connected_players, connected_servers))
            message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
            message_box.setDefaultButton(QMessageBox.Cancel)
            message_box.setEscapeButton(QMessageBox.Cancel)
            message_box.accepted.connect(lambda: System.exit(0))  # TODO: Proper shut down sequence?
            message_box.rejected.connect(lambda: event.setAccepted(False))
            message_box.exec()
        else:
            System.exit(0)

        # super().closeEvent(event)

    def _setup_signals(self) -> None:
        logger.fine("Setting up signals...")
        # Need to do this cos we want the certain processes to be carried out in the right thread

        emitters.ON_ACCOUNT_ADDED.connect(self.account_added.emit)
        emitters.ON_ACCOUNT_ERROR.connect(self.account_error.emit)

        emitters.ON_PLAYER_ADDED.connect(self.player_added.emit)
        emitters.ON_PLAYER_REMOVED.connect(self.player_removed.emit)

        emitters.ON_PLAYER_LOGIN.connect(self.player_login.emit)
        emitters.ON_PLAYER_LOGOUT.connect(self.player_logout.emit)

        emitters.ON_PLAYER_POSITION_UPDATE.connect(self.player_position_update.emit)
        emitters.ON_PLAYER_HEALTH_UPDATE.connect(self.player_health_update.emit)
        emitters.ON_PLAYER_SERVER_STATS_UPDATE.connect(self.player_server_stats_update.emit)

        emitters.ON_CONNECTION_ESTABLISHED.connect(self.connection_established.emit)
        emitters.ON_CONNECTION_LOST.connect(self.connection_lost.emit)

        emitters.ON_PLAYER_JOIN.connect(self.player_joined.emit)
        emitters.ON_PLAYER_LEAVE.connect(self.player_left.emit)
        emitters.ON_PLAYER_GAMEMODE_UPDATE.connect(self.player_gamemode_update.emit)
        emitters.ON_PLAYER_PING_UPDATE.connect(self.player_ping_update.emit)

    def _setup_top_bar(self, main_layout: QVBoxLayout) -> None:
        logger.fine("Setting up top bar...")

        layout = QHBoxLayout()

        self.smiling_imp_label = QLabel(self.central_widget)
        self.smiling_imp_label.setText("\ud83d\ude08")
        layout.addWidget(self.smiling_imp_label)

        layout.addItem(QSpacerItem(40, 20, QSizePolicy.Expanding, QSizePolicy.Minimum))

        self.servers_box = QComboBox(self.central_widget)

        if self.yescom.servers:
            self.current_server = self.yescom.servers[0]
            for server in self.yescom.servers:
                self.servers_box.addItem("%s" % server.hostname)

        self.servers_box.insertSeparator(self.servers_box.count())
        self.servers_box.addItem("Add new server...")
        self.servers_box.currentTextChanged.connect(self._server_changed)
        layout.addWidget(self.servers_box)

        layout.setStretch(1, 3)
        layout.setStretch(2, 1)

        main_layout.addLayout(layout)

    def _setup_tabs(self, main_layout: QVBoxLayout) -> None:
        logger.fine("Setting up tabs...")

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

        self.tab_widget.setCurrentIndex(0)
        main_layout.addWidget(self.tab_widget)

    # ------------------------------ Events ------------------------------ #

    def _server_changed(self, text: str) -> None:
        if not text:
            return

        if text == "Add new server...":  # There's prolly a better way of doing this, I just don't care
            # TODO: Implement
            self.current_server = None
            self.servers_box.setCurrentIndex(-1)
            return
        else:
            for server in self.yescom.servers:
                if text == server.hostname:
                    self.current_server = server
                    logger.fine("Current server: %s" % self._current_server)
                    return

    # ------------------------------ Public methods ------------------------------ #

    def is_selected(self, tab: QTabWidget) -> bool:
        """
        :param tab: The tab to check.
        :return: Is the tab currently selected?
        """

        return self.tab_widget.currentWidget() == tab

    def set_selected(self, tab: QTabWidget) -> None:
        """
        Sets the currently selected tab.

        :param tab: The tab to set as active.
        """

        self.tab_widget.setCurrentIndex(self.tab_widget.indexOf(tab))

    def disconnect_all(self) -> None:
        """
        Disconnects all online players, but warns before doing so.
        """

        if self.current_server is None:
            return

        message_box = QMessageBox(self)
        message_box.setIcon(QMessageBox.Warning)
        message_box.setWindowTitle("Disconnect all")
        message_box.setText("This will disconnect %i account(s)." % len(self.current_server.getPlayers()))
        message_box.setInformativeText("You will also have to enable auto reconnect for all players again, manually.")
        message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
        message_box.setDefaultButton(QMessageBox.Cancel)
        message_box.setEscapeButton(QMessageBox.Cancel)
        message_box.accepted.connect(lambda: self.current_server.disconnectAll("Disconnect all", True))
        message_box.exec()


from .tabs.accounts import AccountsTab
from .tabs.chat_and_logs import ChatAndLogsTab
from .tabs.graphs import GraphsTab
from .tabs.grid_view import GridViewTab
from .tabs.options import OptionsTab
from .tabs.overview import OverviewTab
from .tabs.tasks import TasksTab
from .threads import EventQueueThread, SkinDownloaderThread