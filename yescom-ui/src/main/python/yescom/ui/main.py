#!/usr/bin/env python3

import os
from typing import Dict, List, Tuple, Union

from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from .. import emitters

from java.lang import System

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.core.connection import Server
from ez.pogdog.yescom.ui.config import UIConfig

logger = Logging.getLogger("yescom.ui.window")


def get_font_paths() -> Tuple[List[str], Dict[str, str], List[str]]:
    """
    Returns the paths of the fonts installed on the system.
    https://stackoverflow.com/questions/19098440/how-to-get-the-font-file-path-with-qfont-in-qt

    :return: Unloadable fonts, a dictionary of the fonts and their names, and the paths of the fonts.
    """

    font_paths = QStandardPaths.standardLocations(QStandardPaths.FontsLocation)

    accounted = []
    unloadable = []
    family_to_path = {}

    def find_fonts(path: str) -> None:
        for file in os.listdir(path):
            file = os.path.join(path, file)

            if os.path.isdir(file):
                find_fonts(file)

            elif os.path.splitext(file)[1].lower() in (".ttf", ".otf"):
                idx = db.addApplicationFont(file)  # Add font path

                if idx < 0:
                    unloadable.append(file)  # Font wasn't loaded if idx is -1
                else:
                    names = db.applicationFontFamilies(idx)  # Load back font family name

                    for name in names:
                        if name in family_to_path:
                            accounted.append((name, file))
                        else:
                            family_to_path[name] = file

                    # This isn't a 1:1 mapping, for example
                    # 'C:/Windows/Fonts/HTOWERT.TTF' (regular) and
                    # 'C:/Windows/Fonts/HTOWERTI.TTF' (italic) are different
                    # but applicationFontFamilies will return 'High Tower Text' for both

    db = QFontDatabase()
    for font_path in font_paths:  # Go through all font paths
        if os.path.exists(font_path):
            find_fonts(font_path)  # Go through all files at each path

    # noinspection PyTypeChecker
    return unloadable, family_to_path, accounted


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
    chunk_state = pyqtSignal(object)

    account_added = pyqtSignal(object)
    account_error = pyqtSignal(object)

    player_added = pyqtSignal(object)
    player_removed = pyqtSignal(object)

    player_login = pyqtSignal(object)
    player_logout = pyqtSignal(object)

    player_chat = pyqtSignal(object)

    player_position_update = pyqtSignal(object)
    player_health_update = pyqtSignal(object)
    player_server_stats_update = pyqtSignal(object)

    new_player_cached = pyqtSignal(object)
    trust_state_changed = pyqtSignal(object)
    any_player_joined = pyqtSignal(object)
    any_player_death = pyqtSignal(object)
    any_player_gamemode_update = pyqtSignal(object)
    any_player_ping_update = pyqtSignal(object)
    any_player_left = pyqtSignal(object)

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
        self.config = UIConfig()

        self.setWindowTitle("YesCom \ud83d\ude08")

        logger.fine("Locating fonts...")
        self.unloadable, self.families, self.accounted = get_font_paths()

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

        # FIXME: If we're just gonna do this, there's really no point in having some fancy PyEmitter system, at least in the UI part
        emitters.ON_ACCOUNT_ADDED.connect(self.account_added.emit)
        emitters.ON_ACCOUNT_ERROR.connect(self.account_error.emit)

        emitters.ON_PLAYER_ADDED.connect(self.player_added.emit)
        emitters.ON_PLAYER_REMOVED.connect(self.player_removed.emit)

        emitters.ON_PLAYER_LOGIN.connect(self.player_login.emit)
        emitters.ON_PLAYER_LOGOUT.connect(self.player_logout.emit)

        emitters.ON_PLAYER_CHAT.connect(self.player_chat.emit)

        emitters.ON_PLAYER_POSITION_UPDATE.connect(self.player_position_update.emit)
        emitters.ON_PLAYER_HEALTH_UPDATE.connect(self.player_health_update.emit)
        emitters.ON_PLAYER_SERVER_STATS_UPDATE.connect(self.player_server_stats_update.emit)

        emitters.ON_CONNECTION_ESTABLISHED.connect(self.connection_established.emit)
        emitters.ON_CONNECTION_LOST.connect(self.connection_lost.emit)
        emitters.ON_CHUNK_STATE.connect(self.chunk_state.emit)

        emitters.ON_NEW_PLAYER_CACHED.connect(self.new_player_cached.emit)
        emitters.ON_TRUST_STATE_CHANGED.connect(self.trust_state_changed.emit)
        emitters.ON_ANY_PLAYER_JOIN.connect(self.any_player_joined.emit)
        emitters.ON_ANY_PLAYER_GAMEMODE_UPDATE.connect(self.any_player_gamemode_update.emit)
        emitters.ON_ANY_PLAYER_PING_UPDATE.connect(self.any_player_ping_update.emit)
        emitters.ON_ANY_PLAYER_DEATH.connect(self.any_player_death.emit)
        emitters.ON_ANY_PLAYER_LEAVE.connect(self.any_player_left.emit)

    def _setup_top_bar(self, main_layout: QVBoxLayout) -> None:
        logger.fine("Setting up top bar...")

        layout = QHBoxLayout()

        self.smiling_imp_label = QLabel(self.central_widget)
        self.smiling_imp_label.setText("\ud83d\ude08")
        layout.addWidget(self.smiling_imp_label)

        layout.addItem(QSpacerItem(40, 20, QSizePolicy.Expanding, QSizePolicy.Minimum))

        self.servers_combo_box = QComboBox(self.central_widget)

        if self.yescom.servers:
            self.current_server = self.yescom.servers[0]
            for server in self.yescom.servers:
                self.servers_combo_box.addItem("%s" % server.hostname, server)

        self.servers_combo_box.insertSeparator(self.servers_combo_box.count())
        self.servers_combo_box.addItem("Add new server...")
        self.servers_combo_box.currentIndexChanged.connect(self._on_server_changed)
        layout.addWidget(self.servers_combo_box)

        layout.setStretch(1, 3)
        layout.setStretch(2, 1)

        main_layout.addLayout(layout)

    def _setup_tabs(self, main_layout: QVBoxLayout) -> None:
        logger.fine("Setting up tabs...")

        self.tab_widget = QTabWidget(self.central_widget)
        self.tab_widget.setTabBarAutoHide(True)

        self.overview_tab = OverviewTab(self)
        self.tab_widget.addTab(self.overview_tab, "Overview")

        self.grid_view_tab = GridViewTab(self)
        self.tab_widget.addTab(self.grid_view_tab, "Grid View")

        self.players_tab = PlayersTab(self)
        self.tab_widget.addTab(self.players_tab, "Players")

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

    def _on_server_changed(self, index: int) -> None:
        if self.servers_combo_box.currentData() is None:
            # TODO: Implement
            self.current_server = None
            self.servers_combo_box.setCurrentIndex(-1)
            return
        else:
            self.current_server = self.servers_combo_box.currentData()
            logger.fine("Current server: %s" % self._current_server)

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

        if self._current_server is None or not self._current_server.isConnected():
            return

        online = 0
        for player in self._current_server.getPlayers():
            if player.isConnected():
                online += 1

        message_box = QMessageBox(self)
        message_box.setIcon(QMessageBox.Warning)
        message_box.setWindowTitle("Disconnect all")
        message_box.setText("This will disconnect %i account(s)." % online)
        message_box.setInformativeText("You will also have to enable auto reconnect for ALL players again, manually.")
        message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
        message_box.setDefaultButton(QMessageBox.Cancel)
        message_box.setEscapeButton(QMessageBox.Cancel)
        message_box.accepted.connect(lambda: self.current_server.disconnectAll("Disconnect all", True))
        message_box.exec()


from .tabs.players import PlayersTab
from .tabs.chat_and_logs import ChatAndLogsTab
from .tabs.graphs import GraphsTab
from .tabs.grid_view import GridViewTab
from .tabs.options import OptionsTab
from .tabs.overview import OverviewTab
from .tabs.tasks import TasksTab
from .threads import EventQueueThread, SkinDownloaderThread
