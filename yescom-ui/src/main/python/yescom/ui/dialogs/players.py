#!/usr/bin/env python3

import datetime
from typing import List, Set, Tuple, Union

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data.player import PlayerInfo, Session
from ez.pogdog.yescom.api.data.player.death import Death, Kill
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.connection import Server

logger = Logging.getLogger("yescom.ui.dialogs.players")


class PlayerInfoDialog(QDialog):
    """
    Displays information about any player that YesCom knows about.
    """

    def __init__(self, parent: QWidget, info: PlayerInfo) -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE

        self.info = info

        logger.fine("New player info dialog.")

        self.setWindowTitle("Player info - %s" % info.username)
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.setModal(True)

        self._setup_dialog()
        self._setup_servers()

        self.main_window.trust_state_changed.connect(self._on_trust_state_changed)

    def _setup_dialog(self) -> None:
        logger.finer("Setting up dialog...")

        main_layout = QVBoxLayout(self)

        player_label = QLabel(self)
        player_label.setText("Player: %s" % self.info.username)
        player_label.setToolTip("The player's username.\nSelect to copy.")
        player_label.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse | Qt.TextInteractionFlag.TextSelectableByKeyboard,
        )
        main_layout.addWidget(player_label)

        info_layout = QGridLayout()

        self.skin_label = QLabel(self)
        self.skin_label.setToolTip("The player's skin.")
        info_layout.addWidget(self.skin_label, 0, 0, 3, 1)
        self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)
        self.main_window.skin_downloader_thread.request_skin(self.info.uuid)

        uuid_label = QLabel(self)
        uuid_label.setText("UUID: %s" % self.info.uuid)
        uuid_label.setToolTip("The player's UUID.\nSelect to copy.")
        uuid_label.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse | Qt.TextInteractionFlag.TextSelectableByKeyboard,
        )
        info_layout.addWidget(uuid_label, 0, 1, 1, 1)

        first_seen_label = QLabel(self)
        first_seen_label.setText("First seen: %s" % str(datetime.datetime.fromtimestamp(self.info.firstSeen // 1000)))
        first_seen_label.setToolTip("The first time YesCom saw the player.\nSelect to copy.")
        first_seen_label.setTextInteractionFlags(
            Qt.TextInteractionFlag.TextSelectableByMouse | Qt.TextInteractionFlag.TextSelectableByKeyboard,
        )
        info_layout.addWidget(first_seen_label, 1, 1, 1, 1)

        self.trusted_check_box = QCheckBox(self)
        self.trusted_check_box.setText("Trusted")
        self.trusted_check_box.setToolTip("Is the player trusted? If so, they are exempt from certain checks.")
        self.trusted_check_box.setChecked(self.yescom.playersHandler.isTrusted(self.info.uuid))
        # trusted_check_box.setLayoutDirection(Qt.RightToLeft)
        self.trusted_check_box.stateChanged.connect(self._on_trusted_checkbox_state_changed)
        info_layout.addWidget(self.trusted_check_box, 2, 1, 1, 1)

        info_layout.setColumnStretch(1, 1)
        main_layout.addLayout(info_layout)

        self.servers_label = QLabel(self)
        self.servers_label.setText("Servers (0):")
        main_layout.addWidget(self.servers_label)

        self.servers_tabs = QTabWidget(self)
        main_layout.addWidget(self.servers_tabs)

        dialog_buttons = QDialogButtonBox(self)
        dialog_buttons.setStandardButtons(QDialogButtonBox.StandardButton.Ok)
        dialog_buttons.accepted.connect(self.close)
        main_layout.addWidget(dialog_buttons)

    def _setup_servers(self) -> None:
        logger.finer("Setting up servers...")

        sessions = self.yescom.dataHandler.getSessions(self.info)
        deaths = self.yescom.dataHandler.getDeaths(self.info)
        kills = self.yescom.dataHandler.getKills(self.info)

        servers = set(self.info.servers)
        for session in sessions:
            servers.add(session.server)
        for death in deaths:
            servers.add(death.server)
        for kill in kills:
            servers.add(kill.server)

        if not servers:
            self.servers_tabs.addTab(PlayerInfoDialog.EmptyTab(self), "No servers")
            self.servers_tabs.findChild(QTabBar).setHidden(True)
        else:
            for server in servers:  # Should have been populated by the above line if it wasn't already
                self.servers_tabs.addTab(
                    PlayerInfoDialog.ServerTab(self, self.info, server, sessions, deaths, kills),
                    "%s:%i" % (server.hostname, server.port),
                )

        self.servers_label.setText("Servers (%i):" % len(self.info.servers))

    # ------------------------------ Events ------------------------------ #

    def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
        if resolved[0] == self.info.uuid:
            self.setWindowIcon(QIcon(resolved[1]))
            self.skin_label.setPixmap(resolved[1].scaled(64, 64, transformMode=Qt.TransformationMode.FastTransformation))

    def _on_trusted_checkbox_state_changed(self, state: int) -> None:
        if state == Qt.CheckState.Checked:
            self.yescom.playersHandler.addTrusted(self.info.uuid)
        elif state == Qt.CheckState.Unchecked:
            self.yescom.playersHandler.removeTrusted(self.info.uuid)

    def _on_trust_state_changed(self, info: PlayerInfo) -> None:
        if info == self.info:
            self.trusted_check_box.setChecked(self.yescom.playersHandler.isTrusted(info.uuid))

    # ------------------------------ Classes ------------------------------ #

    class SessionsDialog:
        """
        Displays information about sessions the player has on a given server.
        """

        def __init__(self, parent: "PlayerInfoDialog.ServerTab", info: PlayerInfo, sessions: List[Session]) -> None:
            super().__init__(parent)

            self.player_info = info

            # self.setToolTip("The sessions this player has had on this server.")

            # TODO: Selectable date for between
            # for session in sorted(sessions, key=lambda session: session.start):
            #     start = str(datetime.datetime.fromtimestamp(session.start // 1000))
            #     end = str(datetime.datetime.fromtimestamp(session.end // 1000))
            #     play_time = str(datetime.timedelta(seconds=session.getPlayTime() // 1000))

            #     session_item = QTreeWidgetItem(self, ["Session on %s" % start])
            #     session_item.addChild(QTreeWidgetItem(session_item, ["Start:", start]))
            #     session_item.addChild(QTreeWidgetItem(session_item, ["End:", end]))
            #     session_item.addChild(QTreeWidgetItem(session_item, ["Played:", play_time]))

            #     self.addChild(session_item)

    class DeathsItem(QTreeWidgetItem):
        """
        Displays information about deaths the player has on a given server.
        """

        def __init__(self, parent: QTreeWidget, info: PlayerInfo, deaths: List[Death]) -> None:
            super().__init__(parent, ["Deaths (%i):" % len(deaths)])

            self.info = info

            self.setToolTip(0, "The number of recorded deaths this player has on this server.")

            for death in sorted(deaths, key=lambda death: death.timestamp):
                death_item = QTreeWidgetItem(
                    self, ["Death on %s" % str(datetime.datetime.fromtimestamp(death.timestamp // 1000))],
                )
                death_item.addChild(QTreeWidgetItem(death_item, ["Type:", str(death.type).replace("_", " ").capitalize()]))
                # TODO: Killer

                self.addChild(death_item)

    class KillsItem(QTreeWidgetItem):
        """
        Displays information about kills the player has on a given server.
        """

        def __init__(self, parent: QTreeWidget, info: PlayerInfo, kills: List[Kill]) -> None:
            super().__init__(parent, ["Kills (%i):" % len(kills)])

            self.info = info

            self.setToolTip(0, "The number of recorded kills this player has on this server.")

            # for kill in sorted(kills, key=lambda kill: kill.timestamp):
            #     kill_item = QTreeWidgetItem(self, [""])

    class EmptyTab(QWidget):
        """
        When there are no servers present, just informs the user that there is no data for this player.
        """

        def __init__(self, parent: "PlayerInfoDialog") -> None:
            super().__init__(parent)

            main_layout = QHBoxLayout(self)

            info_label = QLabel(self)
            info_label.setText("No server information present yet.")
            main_layout.addWidget(info_label)

    class ServerTab(QWidget):
        """
        A tab containing information about a server that the player has been seen on.
        """

        def __init__(
            self, parent: "PlayerInfoDialog",
            info: PlayerInfo,
            server: PlayerInfo.ServerInfo,
            sessions: Set[Session],
            deaths: Set[Death],
            kills: Set[Kill],
        ) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.player_info = info
            self.server_info = server

            self.server: Union[Server, None] = None

            self.sessions = []
            self.deaths = []
            self.kills = []

            self.sessions_play_time = 0
            self.current_session_start = -1
            self.current_session_play_time = 0

            for session in sessions:  # Find the sessions that apply only to this server
                if session.server == server:
                    self.sessions_play_time += session.getPlayTime()
                    self.sessions.append(session)

            for death in deaths:
                if death.server == server:
                    self.deaths.append(death)

            for kill in kills:
                if kill.server == server:
                    self.kills.append(kill)

            for server_ in self.yescom.servers:
                if server_.serverInfo == server:
                    self.server = server_

                    if server_.isOnline(info.uuid):
                        self.current_session_start = server_.getSessionStartTime(info.uuid)
                        self.current_session_play_time = server_.getSessionPlayTime(info.uuid)

            self._setup_tab()

            self.main_window.tick.connect(self._on_tick)

            # self.main_window.any_player_joined.connect(self._on_any_player_joined)
            self.main_window.any_player_left.connect(self._on_any_player_left)
            self.main_window.any_player_death.connect(self._on_any_player_death)

        def _setup_tab(self) -> None:
            main_layout = QVBoxLayout(self)

            self.total_play_time_label = QLabel(self)
            self.total_play_time_label.setText(
                "Total play time: " + str(datetime.timedelta(seconds=(self.sessions_play_time + self.current_session_play_time) // 1000)),
            )
            main_layout.addWidget(self.total_play_time_label)

            self.total_deaths_label = QLabel(self)
            self.total_deaths_label.setText("Total deaths: " + str(len(self.deaths)))
            main_layout.addWidget(self.total_deaths_label)

            self.total_kills_label = QLabel(self)
            self.total_kills_label.setText("Total kills: " + str(len(self.kills)))
            main_layout.addWidget(self.total_kills_label)

            # self.sessions_dialog = PlayerInfoDialog.SessionsDialog(self, self.player_info, self.sessions)
            # main_layout.addWidget(self.sessions_dialog)

        # ------------------------------ Events ------------------------------ #

        def _on_tick(self) -> None:
            if self.server.isOnline(self.player_info.uuid):
                self.current_session_start = self.server.getSessionStartTime(self.player_info.uuid)
                self.current_session_play_time = self.server.getSessionPlayTime(self.player_info.uuid)

            self.total_play_time_label.setText(
                "Total play time: " + str(datetime.timedelta(seconds=(self.sessions_play_time + self.current_session_play_time) // 1000)),
            )
            self.total_deaths_label.setText("Total deaths: " + str(len(self.deaths)))
            self.total_kills_label.setText("Total kills: " + str(len(self.kills)))

        # def _on_any_player_joined(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
        #     if online_player_info.info == self.player_info and online_player_info.server == self.server:
        #         ...

        def _on_any_player_left(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
            if online_player_info.info == self.player_info and online_player_info.server == self.server:
                for session in self.player_info.sessions:  # We should have just gotten the new session
                    if not session in self.sessions:
                        self.sessions_play_time += session.getPlayTime()
                        self.sessions.append(session)

        def _on_any_player_death(self, online_player_death: Emitters.OnlinePlayerDeath) -> None:
            if online_player_death.info == self.player_info and online_player_death.server == self.server:
                if not online_player_death.death in self.deaths:
                    self.deaths.append(online_player_death.death)


from ..main import MainWindow
