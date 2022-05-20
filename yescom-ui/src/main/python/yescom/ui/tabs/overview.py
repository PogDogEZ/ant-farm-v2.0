#!/usr/bin/env python3

import math
import time
import webbrowser
from typing import Any

from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data import PlayerInfo
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.connection import Player, Server

logger = Logging.getLogger("yescom.ui.tabs.overview")


class OverviewTab(QTabWidget):
    """
    Provides an overview of the server's information.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.main_window = MainWindow.INSTANCE

        self._last_update = time.time()

        self._setup_tab()

        parent.tick.connect(self._on_tick)

        parent.server_changed.connect(self._on_server_changed)
        # Hack, to make sure we enable the "Disconnect all" button
        parent.connection_established.connect(self._on_server_changed)
        parent.connection_lost.connect(self._on_server_changed)

        parent.player_joined.connect(self._on_player_joined)
        parent.player_left.connect(self._on_player_left)

    def __repr__(self) -> str:
        return "<OverviewTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        main_layout = QHBoxLayout(self)

        info_layout = QVBoxLayout()

        self.address_label = QLabel(self)
        self.address_label.setText("Address: (no server)")
        info_layout.addWidget(self.address_label)

        self.tickrate_label = QLabel(self)
        self.tickrate_label.setText("Tickrate: 0.0tps")
        self.tickrate_label.setToolTip("The current server tickrate.")
        info_layout.addWidget(self.tickrate_label)

        self.ping_label = QLabel(self)
        self.ping_label.setText("Ping: 0ms")
        self.ping_label.setToolTip("The average ping across all connected accounts.")
        info_layout.addWidget(self.ping_label)

        self.tslp_label = QLabel(self)
        self.tslp_label.setText("TSLP: 0ms")
        self.tslp_label.setToolTip("The minimum time since last packet across all connected accounts.")
        info_layout.addWidget(self.tslp_label)

        self.uptime_label = QLabel(self)
        self.uptime_label.setText("Uptime: 00:00:00")
        self.uptime_label.setToolTip("How long we've been connected to this server for.")
        info_layout.addWidget(self.uptime_label)

        self.render_dist_label = QLabel(self)
        self.render_dist_label.setText("Render distance: 0 / 0 (0 chunks)")
        self.render_dist_label.setToolTip("Estimated server render distance.")
        info_layout.addWidget(self.render_dist_label)

        self.queryrate_label = QLabel(self)
        self.queryrate_label.setText("Queryrate: 0.0qps")
        self.queryrate_label.setToolTip("The current rate of queries being sent to the server.")
        info_layout.addWidget(self.queryrate_label)

        # TODO: More information (trackers, etc)
        # TODO: Current (in game) time?

        main_layout.addLayout(info_layout)
        main_layout.addItem(QSpacerItem(40, 20, QSizePolicy.Expanding, QSizePolicy.Minimum))

        online_layout = QVBoxLayout()

        self.online_players_label = QLabel(self)
        self.online_players_label.setText("Online players (0):")
        online_layout.addWidget(self.online_players_label)

        self.online_players_tree = OverviewTab.OnlinePlayersTree(self)
        online_layout.addWidget(self.online_players_tree)

        buttons_layout = QHBoxLayout()

        self.remove_button = QPushButton(self)
        self.remove_button.setEnabled(self.main_window.current_server is not None)
        self.remove_button.setText("Remove server")
        buttons_layout.addWidget(self.remove_button)

        self.disconnect_all_button = QPushButton(self)
        self.disconnect_all_button.setEnabled(
            self.main_window.current_server is not None and self.main_window.current_server.isConnected()
        )
        self.disconnect_all_button.setText("Disconnect all")
        self.disconnect_all_button.setToolTip("Disconnects all currently online players and disables auto reconnecting.")
        self.disconnect_all_button.clicked.connect(lambda checked: self.main_window.disconnect_all())
        buttons_layout.addWidget(self.disconnect_all_button)

        online_layout.addLayout(buttons_layout)
        main_layout.addLayout(online_layout)

        main_layout.setStretch(1, 1)
        main_layout.setStretch(2, 10)

    # ------------------------------ Events ------------------------------ #

    def _on_tick(self) -> None:
        if time.time() - self._last_update < .1:  # Don't want it to update too fast
            return
        self._last_update = time.time()
        current = self.main_window.current_server

        address = "(no server)"
        tickrate = 0.0
        ping = 0.0
        tslp = "0"
        uptime = "00:00:00"
        render_distance = 0
        queryrate = 0.0

        if current is not None:
            address = "%s:%i" % (current.hostname, current.port)
            if current.isConnected():
                tickrate = current.getTPS()
                ping = current.getPing()
                tslp = max(1, current.getTSLP())
                if tslp > current.HIGH_TSLP.value:
                    tslp = str(tslp)
                else:
                    tslp = "<%i" % (math.ceil(tslp / 50) * 50)
                uptime = time.strftime("%H:%M:%S", time.gmtime(current.getConnectionTime()))
                queryrate = current.getQPS()

            render_distance = current.getRenderDistance()

        self.address_label.setText("Address: %s" % address)
        self.tickrate_label.setText("Tickrate: %.1ftps" % tickrate)
        self.ping_label.setText("Ping: %.1fms" % ping)
        self.tslp_label.setText("TSLP: %sms" % tslp)
        self.uptime_label.setText("Uptime: %s" % uptime)
        self.render_dist_label.setText("Render distance: %i / %i (%i chunks)" % (
            max(0, (render_distance - 1) // 2), render_distance, render_distance ** 2,
        ))
        self.queryrate_label.setText("Queryrate: %.1fqps" % queryrate)

    def _on_server_changed(self) -> None:
        count = 0
        if self.main_window.current_server is not None:
            count = len(self.main_window.current_server.onlinePlayers)

        self.online_players_label.setText("Online players (%i):" % count)
        self.disconnect_all_button.setEnabled(
            self.main_window.current_server is not None and self.main_window.current_server.isConnected()
        )

    def _on_player_joined(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
        for top_level_index in range(self.online_players_tree.topLevelItemCount()):
            if self.online_players_tree.topLevelItem(top_level_index).info == online_player_info.info:
                return
        self._on_server_changed()
        self.online_players_tree.addTopLevelItem(OverviewTab.OnlinePlayerItem(
            self.online_players_tree, online_player_info.server, online_player_info.info,
        ))

    def _on_player_left(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
        for top_level_index in range(self.online_players_tree.topLevelItemCount()):
            item = self.online_players_tree.topLevelItem(top_level_index)
            if item.info == online_player_info.info and item.server == online_player_info.server:
                self._on_server_changed()
                self.online_players_tree.takeTopLevelItem(top_level_index)
                return

    # ------------------------------ Classes ------------------------------ #

    class OnlinePlayersTree(QTreeWidget):
        """
        Tree widget containing information about players currently online.
        """

        def __init__(self, parent: "OverviewTab") -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.setHeaderHidden(True)
            self.setColumnCount(2)

            self.itemChanged.connect(lambda item: self.resizeColumnToContents(0))

        # ------------------------------ Events ------------------------------ #

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            current = self.currentItem()
            if current is None:
                return

            menu = QMenu()
            clipboard = QApplication.clipboard()

            if isinstance(current, OverviewTab.OnlinePlayerItem):
                info = current.info
                server = current.server
            elif isinstance(current.parent(), OverviewTab.OnlinePlayerItem):
                info = current.parent().info
                server = current.parent().server
            else:
                logger.warning("Don't know how to handle item %s." % current)
                return

            player = server.getPlayer(info.uuid)

            trusted = menu.addAction("Trusted", lambda: self._toggle_trusted(info.uuid))
            trusted.setCheckable(True)
            trusted.setChecked(self.yescom.playersHandler.isTrusted(info.uuid))
            trusted.setEnabled(player is None)  # Can't untrust players that we "own"

            menu.addSeparator()

            view_player = menu.addAction("View player", lambda: self._view_player(player))
            view_player.setEnabled(player is not None)

            menu.addSeparator()

            menu.addAction("Open NameMC...", lambda: webbrowser.open("https://namemc.com/profile/%s" % info.uuid))

            menu.addSeparator()

            menu.addAction(
                "Copy username",
                lambda: clipboard.setText(self.yescom.playersHandler.getName(info.uuid, "<unknown name>")),
            )
            menu.addAction("Copy UUID", lambda: clipboard.setText(str(info.uuid)))

            menu.exec(event.globalPos())

        # ------------------------------ Utility methods ------------------------------ #

        def _toggle_trusted(self, uuid: UUID) -> None:
            if self.yescom.playersHandler.isTrusted(uuid):
                self.yescom.playersHandler.removeTrusted(uuid)
            else:
                self.yescom.playersHandler.addTrusted(uuid)

        def _view_player(self, player: Player) -> None:
            if self.main_window.current_server != player.server:
                self.main_window.current_server = player.server  # Switch to the player's server if we're not viewing it

            self.main_window.accounts_tab.select(player)
            self.main_window.accounts_tab.expand(player)
            self.main_window.set_selected(self.main_window.accounts_tab)

    class OnlinePlayerItem(QTreeWidgetItem):
        """
        Information about any player online on the server.
        """

        tooltip = "%s\nServer: %s:%i.\nPing: %%ims\nGamemode: %%s\nRight click for more options."

        def __init__(self, parent: "OverviewTab.OnlinePlayersTree", server: Server, info: PlayerInfo) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.server = server
            self.info = info

            uuid = info.uuid  # Crazy optimisations
            name = self.yescom.playersHandler.getName(uuid, str(uuid))  # When would this not resolve?
            known = server.hasPlayer(uuid)

            self.tooltip = self.tooltip % (name, server.hostname, server.port)
            self.setText(0, name)
            self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))
            if known:
                font = self.font(0)
                font.setBold(True)
                self.setFont(0, font)

            self.main_window.skin_downloader_thread.request_skin(uuid, lambda icon: self.setIcon(0, icon))

            for index in range(4):
                self.addChild(QTreeWidgetItem(self, []))

            self.child(0).setText(0, "UUID:")
            self.child(0).setText(1, str(uuid))
            self.child(0).setToolTip(0, "The UUID of the player.")
            self.child(0).setToolTip(1, self.child(0).text(1))

            self.child(1).setText(0, "Known:")
            self.child(1).setText(1, str(known))
            self.child(1).setToolTip(0, "Is this a player that we \"control\" / know?")
            self.child(1).setToolTip(1, self.child(1).text(1))

            self.child(2).setText(0, "Ping:")
            self.child(2).setToolTip(0, "The latency that the server estimates this player has.")
            self._on_ping_update(Emitters.OnlinePlayerInfo(self.info, self.server))

            self.child(3).setText(0, "Gamemode:")
            self.child(3).setToolTip(0, "The gamemode of the player.")
            self._on_gamemode_update(Emitters.OnlinePlayerInfo(self.info, self.server))

            # TODO: More information, are they being tracked, current session time, etc...

            self.main_window.server_changed.connect(self._on_server_change)

            self.main_window.player_gamemode_update.connect(self._on_gamemode_update)
            self.main_window.player_ping_update.connect(self._on_ping_update)

        def __eq__(self, other: Any) -> bool:
            return isinstance(other, OverviewTab.OnlinePlayerItem) and other.info == self.info

        def _on_server_change(self) -> None:
            self.setHidden(self.main_window.current_server != self.server)

        def _on_gamemode_update(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
            if online_player_info.info == self.info and online_player_info.server == self.server:
                info = online_player_info.info
                self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))

                self.child(3).setText(1, str(info.gameMode).lower())
                self.child(3).setToolTip(1, self.child(3).text(1))

        def _on_ping_update(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
            if online_player_info.info == self.info and online_player_info.server == self.server:
                info = online_player_info.info
                self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))

                self.child(2).setText(1, "%ims" % info.ping)
                self.child(2).setToolTip(1, self.child(2).text(1))


from ..main import MainWindow
