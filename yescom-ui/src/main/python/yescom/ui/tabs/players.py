#!/usr/bin/env python3

import datetime
import webbrowser
from typing import Any, Tuple, Union

from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data.player import PlayerInfo
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.account import IAccount
from ez.pogdog.yescom.core.account.accounts import Microsoft, Mojang
from ez.pogdog.yescom.core.config import Option
from ez.pogdog.yescom.core.connection import Player

logger = Logging.getLogger("yescom.ui.tabs.players")


class PlayersTab(QWidget):
    """
    Allows you to view players and manage accounts that are associated with YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = parent

        self._processing_account: Union[IAccount, None] = None

        self.main_window.server_changed.connect(self._on_server_changed)
        self.main_window.connection_established.connect(self._on_server_changed)
        self.main_window.connection_lost.connect(self._on_server_changed)

        self.main_window.account_added.connect(self._on_account_added)
        self.main_window.account_error.connect(self._on_account_error)

        self.main_window.player_added.connect(self._on_player_added)
        self.main_window.player_removed.connect(self._on_player_removed)

        self.main_window.new_player_cached.connect(self._on_new_player_cached)

        self._setup_tab()

        for server in self.yescom.servers:  # Players from accounts.txt will already exist by now, so need to add them
            for player in server.getPlayers():
                self._on_player_added(player)

        # Also need to add the already-cached players
        for info in self.yescom.playersHandler.getPlayerCache().values():
            self._on_new_player_cached(info, skip_check=True)  # No need to check for duplicates here

    def __repr__(self) -> str:
        return "<PlayersTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        logger.finer("Setting up players tab...")

        main_layout = QHBoxLayout(self)

        accounts_layout = QVBoxLayout()

        self.accounts_label = QLabel(self)
        self.accounts_label.setText("Accounts (0):")
        self.accounts_label.setToolTip("Accounts that we own and can use.")
        accounts_layout.addWidget(self.accounts_label)

        self.accounts_tree = PlayersTab.AccountsTree(self)
        accounts_layout.addWidget(self.accounts_tree)

        credentials_layout = QGridLayout()

        username_label = QLabel(self)
        username_label.setText("Username:")
        credentials_layout.addWidget(username_label, 0, 0, 1, 1)

        password_label = QLabel(self)
        password_label.setText("Password:")
        credentials_layout.addWidget(password_label, 1, 0, 1, 1)

        self.username_edit = QLineEdit(self)
        self.username_edit.returnPressed.connect(lambda: self.password_edit.setFocus())
        self.username_edit.textChanged.connect(self._on_username_changed)
        credentials_layout.addWidget(self.username_edit, 0, 1, 1, 1)

        self.password_edit = QLineEdit(self)
        self.password_edit.setEchoMode(QLineEdit.Password)
        self.password_edit.returnPressed.connect(lambda: self._on_mojang_login(False))  # TODO: Microsoft login?
        self.password_edit.textChanged.connect(self._on_password_changed)
        credentials_layout.addWidget(self.password_edit, 1, 1, 1, 1)

        accounts_layout.addLayout(credentials_layout)

        login_buttons_layout = QHBoxLayout()

        self.mojang_login_button = QPushButton(self)
        self.mojang_login_button.setText("Mojang login")
        self.mojang_login_button.setEnabled(False)
        self.mojang_login_button.clicked.connect(self._on_mojang_login)
        login_buttons_layout.addWidget(self.mojang_login_button)

        self.microsoft_login_button = QPushButton(self)
        self.microsoft_login_button.setText("Microsoft login")
        self.microsoft_login_button.setEnabled(False)
        self.microsoft_login_button.clicked.connect(self._on_microsoft_login)
        login_buttons_layout.addWidget(self.microsoft_login_button)

        accounts_layout.addLayout(login_buttons_layout)

        self.disconnect_all_button = QPushButton(self)
        self.disconnect_all_button.setEnabled(
            self.main_window.current_server is not None and self.main_window.current_server.isConnected()
        )
        self.disconnect_all_button.setText("Disconnect all")
        self.disconnect_all_button.setToolTip("Disconnects all currently online players and disables auto reconnecting.")
        self.disconnect_all_button.clicked.connect(lambda checked: self.main_window.disconnect_all())
        accounts_layout.addWidget(self.disconnect_all_button)

        main_layout.addLayout(accounts_layout)

        players_layout = QVBoxLayout()

        self.players_label = QLabel(self)
        self.players_label.setText("All players (0):")
        self.players_label.setToolTip("All the players that YesCom has seen across all servers, not just the ones online.")
        players_layout.addWidget(self.players_label)

        self.players_tree = PlayersTab.PlayersTree(self)
        players_layout.addWidget(self.players_tree)

        # manage_players = QPushButton(self)
        # manage_players.setText("Manage players")
        # manage_players.clicked.connect(self._on_manage_players)
        # players_layout.addWidget(manage_players)

        main_layout.addLayout(players_layout)

    # ------------------------------ Events ------------------------------ #

    def _on_server_changed(self) -> None:
        count = 0
        if self.main_window.current_server is not None:
            count = len(self.main_window.current_server.getPlayers())

        self.accounts_label.setText("Accounts (%i):" % count)
        self.disconnect_all_button.setEnabled(
            self.main_window.current_server is not None and self.main_window.current_server.isConnected()
        )

    def _on_account_added(self, account: IAccount) -> None:
        if account == self._processing_account:
            # QMessageBox.information(self, "Success", "Account was successfully logged in.")

            self._processing_account = None

            self.username_edit.setEnabled(True)
            self.username_edit.clear()
            self.password_edit.setEnabled(True)
            self.password_edit.clear()

            QApplication.restoreOverrideCursor()

    def _on_account_error(self, account_error: Emitters.AccountError) -> None:
        if account_error.account == self._processing_account:
            message = str(account_error.error)
            if account_error.error.getCause() is not None:
                message = str(account_error.error.getCause())
            QMessageBox.warning(self, "Failed", "Failed to log in account: %r." % message)

            self._processing_account = None

            self.username_edit.setEnabled(True)
            self.password_edit.setEnabled(True)
            self.password_edit.clear()

            QApplication.restoreOverrideCursor()

    def _on_player_added(self, player: Player) -> None:
        for top_level_index in range(self.accounts_tree.topLevelItemCount()):
            if player == self.accounts_tree.topLevelItem(top_level_index).player:
                return
        self._on_server_changed()  # Update label
        self.accounts_tree.addTopLevelItem(PlayersTab.AccountItem(self.accounts_tree, player))

    def _on_player_removed(self, player: Player) -> None:
        for top_level_index in range(self.accounts_tree.topLevelItemCount()):
            if player == self.accounts_tree.topLevelItem(top_level_index).player:
                self._on_server_changed()
                self.accounts_tree.takeTopLevelItem(top_level_index)
                return

    def _on_new_player_cached(self, info: PlayerInfo, skip_check: bool = False) -> None:
        if not skip_check:
            # Should hopefully happen less often as the number of players increases
            for top_level_index in range(self.players_tree.topLevelItemCount()):
                if info == self.players_tree.topLevelItem(top_level_index).info:
                    return
        self.players_label.setText("All players (%i):" % len(self.yescom.playersHandler.getPlayerCache()))
        self.players_tree.addTopLevelItem(PlayersTab.PlayerItem(self.players_tree, info))

    def _on_username_changed(self, text: str) -> None:
        self.mojang_login_button.setEnabled(len(text) > 3 and len(self.password_edit.text()) > 3)
        self.microsoft_login_button.setEnabled(self.mojang_login_button.isEnabled())

    def _on_password_changed(self, text: str) -> None:
        self.mojang_login_button.setEnabled(len(text) > 3 and len(self.username_edit.text()) > 3)
        self.microsoft_login_button.setEnabled(self.mojang_login_button.isEnabled())

    def _on_mojang_login(self, checked: bool) -> None:
        logger.fine("Mojang login fired.")

        username = self.username_edit.text()
        password = self.password_edit.text()
        if len(username) > 3 and len(password) > 3:
            self._processing_account = Mojang(username, password)
            self.username_edit.setEnabled(False)
            self.password_edit.setEnabled(False)
            self.mojang_login_button.setEnabled(False)
            self.microsoft_login_button.setEnabled(False)

            QApplication.setOverrideCursor(Qt.WaitCursor)
            self.yescom.accountHandler.addAccount(self._processing_account)

    def _on_microsoft_login(self, checked: bool) -> None:
        logger.fine("Microsoft login fired.")

        username = self.username_edit.text()
        password = self.password_edit.text()
        if len(username) > 3 and len(password) > 3:
            self._processing_account = Microsoft(username, password)
            self.username_edit.setEnabled(False)
            self.password_edit.setEnabled(False)
            self.mojang_login_button.setEnabled(False)
            self.microsoft_login_button.setEnabled(False)

            QApplication.setOverrideCursor(Qt.WaitCursor)
            self.yescom.accountHandler.addAccount(self._processing_account)

    # ------------------------------ Public methods ------------------------------ #

    def select_account(self, player: Player) -> None:
        """
        Selects the given player in the accounts list.

        :param player: The player to select.
        """

        for top_level_index in range(self.accounts_tree.topLevelItemCount()):
            item = self.accounts_tree.topLevelItem(top_level_index)
            if item.player == player:
                self.accounts_tree.setCurrentIndex(self.accounts_tree.indexFromItem(item))
                return

    def expand_account(self, player: Player) -> None:
        """
        Expands the menu on the given player.

        :param player: The player whose menu to expand.
        """

        for top_level_index in range(self.accounts_tree.topLevelItemCount()):
            item = self.accounts_tree.topLevelItem(top_level_index)
            if item.player == player:
                self.accounts_tree.expand(self.accounts_tree.indexFromItem(item))
                return

    # ------------------------------ Classes ------------------------------ #

    class AccountsTree(QTreeWidget):
        """
        Stores players in a tree view.
        """

        def __init__(self, parent: "PlayersTab") -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.dirty = False
            self._connect_thread: Union[PlayersTab.ConnectThread, None] = None

            self.setHeaderHidden(True)
            self.setColumnCount(2)

            self.itemChanged.connect(lambda item: self._set_dirty())

            self.main_window.tick.connect(self._on_tick)

        # ------------------------------ Events ------------------------------ #

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            current = self.currentItem()
            if current is None:
                return

            menu = QMenu()
            clipboard = QApplication.clipboard()

            if isinstance(current, PlayersTab.AccountItem):
                player = current.player
            elif isinstance(current.parent(), PlayersTab.AccountItem):
                player = current.parent().player
            else:
                logger.warning("Don't know how to handle item %s." % current)
                return

            # TODO: Proper options integration?
            auto_reconnect = menu.addAction("Auto reconnect", lambda: self._toggle(player.AUTO_RECONNECT))
            auto_reconnect.setCheckable(True)
            auto_reconnect.setChecked(player.AUTO_RECONNECT.value)
            # auto_reconnect.setToolTip("Allow this player to automatically reconnect.")

            disable_auto_reconnect_on_auto_logout = menu.addAction(  # TODO: Might need a better name tbh
                "Disable on autolog",
                lambda: self._toggle(player.DISABLE_AUTO_RECONNECT_ON_LOGOUT),
            )
            disable_auto_reconnect_on_auto_logout.setCheckable(True)
            disable_auto_reconnect_on_auto_logout.setChecked(player.DISABLE_AUTO_RECONNECT_ON_LOGOUT.value)

            menu.addSeparator()  # To make clear that the above are exclusive to each other

            connect = menu.addAction("Connect", lambda: self._connect(player))
            connect.setEnabled(self._connect_thread is None and not player.isConnected())
            # connect.setToolTip("Connects this player to the currently selected server.")

            disconnect = menu.addAction("Disconnect", lambda: player.disconnect("User disconnect"))
            disconnect.setEnabled(player.isConnected())
            # disconnect.setToolTip("Disconnects this player from the currently selected server.")

            remove = menu.addAction("Remove", lambda: self._remove(player))
            remove.setEnabled(player.server.hasPlayer(player))
            # remove.setEnabled(self.yescom.accountHandler.hasAccount(player.account))
            # remove.setToolTip("Removes this player's account from the account cache.")

            menu.addSeparator()

            menu.addAction("Copy username", lambda: clipboard.setText(player.getUsername()))
            menu.addAction("Copy UUID", lambda: clipboard.setText(str(player.getUUID())))

            menu.addSeparator()

            copy_coords = menu.addAction(
                "Copy coords",
                lambda: clipboard.setText("%.1f, %.1f, %.1f" % (
                    player.getPosition().getX(),
                    player.getPosition().getY(),
                    player.getPosition().getZ(),
                )),
            )
            copy_coords.setEnabled(player.isConnected())
            copy_angle = menu.addAction(
                "Copy angle",
                lambda: clipboard.setText("%.1f, %.1f" % (
                    player.getAngle().getYaw(),
                    player.getAngle().getPitch(),
                )),
            )
            copy_angle.setEnabled(player.isConnected())
            copy_dimension = menu.addAction(
                "Copy dimension",
                lambda: clipboard.setText(str(player.getDimension()).lower()),
            )
            copy_dimension.setEnabled(player.isConnected())

            # TODO: Goto player, view disconnect logs, view chat

            menu.exec(event.globalPos())

        def _on_tick(self) -> None:
            if self.dirty:
                self.resizeColumnToContents(0)
                self.dirty = False

        def _on_connect(self) -> None:
            player = self._connect_thread.player
            self._connect_thread = None
            QApplication.restoreOverrideCursor()

            if not player.isConnected():
                QMessageBox.warning(self, "Couldn't connect", "Couldn't connect player %r to %s:%i." %
                                          (player.getUsername(), player.server.hostname, player.server.port))

        # ------------------------------ Utility methods ------------------------------ #

        def _set_dirty(self) -> None:
            self.dirty = True

        def _toggle(self, option: Option) -> None:
            """
            Toggles a boolean option's value.
            """

            option.value = not option.value

        def _connect(self, player: Player) -> None:
            """
            Connects a player to the currently selected server.
            """

            if self._connect_thread is None and not player.isConnected():
                QApplication.setOverrideCursor(Qt.WaitCursor)
                self._connect_thread = PlayersTab.ConnectThread(self, player)
                self._connect_thread.finished.connect(self._on_connect)
                self._connect_thread.start()

        def _remove(self, player: Player) -> None:
            """
            Removes a player's account from the cache and displays that it has been removed.
            """

            if self.yescom.accountHandler.hasAccount(player.account):
                self.yescom.accountHandler.removeAccount(player.account)

            if player.isConnected():
                player.disconnect("Removed by user")
            player.server.removePlayer(player)
            QMessageBox.information(self, "Success", "The player %r's account has been removed." % player.getUsername())

    class AccountItem(QTreeWidgetItem):
        """
        Information about an account / player that we "control".
        """

        connected_tooltip = "%s\nServer: %s:%i\nConnected: %%s\nTPS: %%.1ftps\nPing: %%ims\nHealth: %%.1f\nRight click for more options."
        disconnected_tooltip = "%s\nServer: %s:%i\nConnected: %%s\nRight click for more options."

        def __init__(self, parent: "PlayersTab.AccountsTree", player: Player):
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.player = player

            username = player.getUsername()  # Local lookups are a ton faster
            uuid = player.getUUID()
            hostname = player.server.hostname
            port = player.server.port

            self.connected_tooltip = self.connected_tooltip % (username, hostname, port)
            self.disconnected_tooltip = self.disconnected_tooltip % (username, hostname, port)

            self.setText(0, player.getUsername())

            self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)
            self.main_window.skin_downloader_thread.request_skin(uuid)

            uuid_child = QTreeWidgetItem(self, ["UUID:", str(uuid)])
            uuid_child.setToolTip(0, "The player's UUID.")
            uuid_child.setToolTip(1, str(uuid))
            self.addChild(uuid_child)

            self.connected_child = QTreeWidgetItem(self, ["Connected:"])
            self.connected_child.setToolTip(0, "Is the player connected to the server?")
            self.addChild(self.connected_child)

            self.position_child = QTreeWidgetItem(self, ["Position:"])
            self.position_child.setToolTip(0, "The current position of the player.")
            self.addChild(self.position_child)
            self.angle_child = QTreeWidgetItem(self, ["Angle:"])
            self.angle_child.setToolTip(0, "The current angle (yaw, pitch) of the player.")
            self.addChild(self.angle_child)
            self.dimension_child = QTreeWidgetItem(self, ["Dimension:"])
            self.dimension_child.setToolTip(0, "The dimension the player is currently in.")
            self.addChild(self.dimension_child)
            self._on_position_update(player)

            self.health_child = QTreeWidgetItem(self, ["Health:"])
            self.health_child.setToolTip(0, "The current health of the player.")
            self.addChild(self.health_child)
            self.hunger_child = QTreeWidgetItem(self, ["Hunger:"])
            self.hunger_child.setToolTip(0, "The current hunger level of the player.")
            self.addChild(self.hunger_child)
            self.saturation_child = QTreeWidgetItem(self, ["Saturation:"])
            self.saturation_child.setToolTip(0, "The current saturation level of the player.")
            self.addChild(self.saturation_child)
            self._on_health_update(player)

            self.tickrate_child = QTreeWidgetItem(self, ["Server tickrate:"])
            self.tickrate_child.setToolTip(0, "The current TPS that this player estimates the server is running at.")
            self.addChild(self.tickrate_child)
            self.ping_child = QTreeWidgetItem(self, ["Server ping:"])
            self.ping_child.setToolTip(0, "The ping that the server estimates this player has.")
            self.addChild(self.ping_child)
            self.chunks_child = QTreeWidgetItem(self, ["Chunks:"])
            self.chunks_child.setToolTip(0, "The number of chunks in the render distance of this player.")
            self.addChild(self.chunks_child)
            self._on_server_stats_update(player)

            self._on_login(player)
            self._on_server_change()  # Update hidden

            # TODO: Last position if logged out
            # TODO: Failed connection attempts

            self.main_window.server_changed.connect(self._on_server_change)

            self.main_window.player_login.connect(self._on_login)
            self.main_window.player_logout.connect(self._on_logout)

            self.main_window.player_position_update.connect(self._on_position_update)
            self.main_window.player_health_update.connect(self._on_health_update)
            self.main_window.player_server_stats_update.connect(self._on_server_stats_update)

        def __eq__(self, other) -> bool:
            return isinstance(other, PlayersTab.AccountItem) and other.player == self.player

        # ------------------------------ Events ------------------------------ #

        def _on_skin_resolved(self, resolved: Tuple[UUID, QIcon]) -> None:
            if resolved[0] == self.player.getUUID():
                self.setIcon(0, resolved[1])

        def _on_server_change(self) -> None:
            self.setHidden(self.main_window.current_server != self.player.server)

        def _on_login(self, player: Player) -> None:  # Not strictly on login, who cares though
            if player == self.player:
                connected = player.isConnected()
                if connected:
                    self.setToolTip(0, self.connected_tooltip % (
                        True, player.getServerTPS(), player.getServerPing(), player.getHealth(),
                    ))
                    self.setForeground(0, QColor(0, 128, 0))  # TODO: Config options for colours
                else:
                    self.setToolTip(0, self.disconnected_tooltip % False)
                    self.setForeground(0, QColor(200, 0, 0))

                self.connected_child.setText(1, str(connected))
                self.connected_child.setToolTip(1, self.child(1).text(1))

                self.position_child.setHidden(not connected)
                self.angle_child.setHidden(not connected)
                self.dimension_child.setHidden(not connected)

                self.health_child.setHidden(not connected)
                self.hunger_child.setHidden(not connected)
                self.saturation_child.setHidden(not connected)

                self.tickrate_child.setHidden(not connected)
                self.ping_child.setHidden(not connected)
                self.chunks_child.setHidden(not connected)

        def _on_logout(self, player_logout: Emitters.PlayerLogout) -> None:
            self._on_login(player_logout.player)

        def _on_position_update(self, player: Player) -> None:
            if player == self.player:
                position = player.getPosition()
                angle = player.getAngle()

                self.position_child.setText(1, "%.1f, %.1f, %.1f" % (position.getX(), position.getY(), position.getZ()))
                self.position_child.setToolTip(1, self.position_child.text(1))
                self.angle_child.setText(1, "%.1f, %.1f" % (angle.getYaw(), angle.getPitch()))
                self.angle_child.setToolTip(1, self.angle_child.text(1))
                self.dimension_child.setText(1, str(player.getDimension()).lower())
                self.dimension_child.setToolTip(1, str(player.getDimension()).lower())

        def _on_health_update(self, player: Player) -> None:
            if player == self.player:
                self.setToolTip(0, self.connected_tooltip % (
                    player.isConnected(), player.getServerTPS(), player.getServerPing(), player.getHealth(),
                ))

                self.health_child.setText(1, "%.1f" % player.getHealth())
                self.health_child.setToolTip(1, self.health_child.text(1))
                self.hunger_child.setText(1, "%i" % player.getHunger())
                self.hunger_child.setToolTip(1, self.hunger_child.text(1))
                self.saturation_child.setText(1, "%.1f" % player.getSaturation())
                self.saturation_child.setToolTip(1, self.saturation_child.text(1))

        def _on_server_stats_update(self, player: Player) -> None:
            if player == self.player:
                self.setToolTip(0, self.connected_tooltip % (
                    player.isConnected(), player.getServerTPS(), player.getServerPing(), player.getHealth(),
                ))

                self.tickrate_child.setText(1, "%.1ftps" % player.getServerTPS())
                self.tickrate_child.setToolTip(1, self.tickrate_child.text(1))
                self.ping_child.setText(1, "%ims" % player.getServerPing())
                self.ping_child.setToolTip(1, self.ping_child.text(1))
                self.chunks_child.setText(1, "%i" % len(player.loadedChunks))
                self.chunks_child.setToolTip(1, self.chunks_child.text(1))

    class PlayersTree(QTreeWidget):  # TODO: Search by username
        """
        Tree widget containing information about players currently online.
        """

        def __init__(self, parent: "PlayersTab") -> None:
            """
            :param parent: The parent widget.
            """

            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.dirty = False

            self.setHeaderHidden(True)
            self.setColumnCount(2)

            self.itemChanged.connect(lambda item: self._set_dirty())

            self.main_window.tick.connect(self._on_tick)

        # ------------------------------ Events ------------------------------ #

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            current = self.currentItem()
            if current is None:
                return

            menu = QMenu()
            clipboard = QApplication.clipboard()

            if isinstance(current, PlayersTab.PlayerItem):
                info = current.info
            elif isinstance(current.parent(), PlayersTab.PlayerItem):
                info = current.parent().info
            else:
                logger.warning("Don't know how to handle item %s." % current)
                return

            trusted = menu.addAction("Trusted", lambda: self._toggle_trusted(info.uuid))
            trusted.setCheckable(True)
            trusted.setChecked(self.yescom.playersHandler.isTrusted(info.uuid))

            menu.addSeparator()

            menu.addAction("Open NameMC...", lambda: webbrowser.open("https://namemc.com/profile/%s" % info.uuid))
            menu.addSeparator()
            menu.addAction(
                "Copy username",
                lambda: clipboard.setText(self.yescom.playersHandler.getName(info.uuid, "<unknown name>")),
            )
            menu.addAction("Copy UUID", lambda: clipboard.setText(str(info.uuid)))

            menu.exec(event.globalPos())

        def _on_tick(self) -> None:
            if self.dirty:
                self.resizeColumnToContents(0)
                self.dirty = False

        # ------------------------------ Utility methods ------------------------------ #

        def _set_dirty(self) -> None:
            self.dirty = True

        def _toggle_trusted(self, uuid: UUID) -> None:
            if self.yescom.playersHandler.isTrusted(uuid):
                self.yescom.playersHandler.removeTrusted(uuid)
            else:
                self.yescom.playersHandler.addTrusted(uuid)

        def _view_account(self, player: Player) -> None:
            if self.main_window.current_server != player.server:
                # Switch to the player's server if we're not viewing it, weird that we wouldn't be though
                self.main_window.current_server = player.server

            self.main_window.players_tab.select_account(player)
            self.main_window.players_tab.expand_account(player)
            self.main_window.set_selected(self.main_window.players_tab)

    class PlayerItem(QTreeWidgetItem):
        """
        Information about any player online on the server.
        """

        def __init__(self, parent: "PlayersTab.PlayersTree", info: PlayerInfo) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.info = info

            uuid = info.uuid
            name = self.yescom.playersHandler.getName(uuid, str(uuid))

            self.setText(0, name)
            # self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))

            # TODO: Icons? They might start to fill up memory, perhaps only if the player is online
            # self.main_window.skin_downloader_thread.request_skin(
            #     uuid, lambda icon: self.setIcon(0, icon), parent.automatic_skins,
            # )

            uuid_child = QTreeWidgetItem(self, ["UUID", str(uuid)])
            uuid_child.setToolTip(0, "The UUID of the player.")
            uuid_child.setToolTip(1, str(uuid))
            self.addChild(uuid_child)

            # TODO: Sessions, are we tracking them, etc...

            trusted_child = QTreeWidgetItem(self, ["Trusted:"])
            trusted_child.setToolTip(0, "Is this a player that we trust?")
            self.addChild(trusted_child)
            self._on_trust_state_changed(info)

            first_seen_child = QTreeWidgetItem(
                self, ["First seen:", str(datetime.datetime.fromtimestamp(info.firstSeen // 1000))],
            )
            first_seen_child.setToolTip(0, "The first time that YesCom saw the player (across all servers).")
            first_seen_child.setToolTip(1, first_seen_child.text(1))
            self.addChild(first_seen_child)

            self.main_window.trust_state_changed.connect(self._on_trust_state_changed)

        def __eq__(self, other: Any) -> bool:
            return isinstance(other, PlayersTab.PlayerItem) and other.info == self.info

        def _on_trust_state_changed(self, info: PlayerInfo) -> None:
            if info == self.info:
                trusted = self.yescom.playersHandler.isTrusted(info.uuid)
                if trusted:
                    self.setForeground(0, QColor(0, 117, 163))  # TODO: Configurable
                else:
                    self.setForeground(0, QApplication.palette().text())

                self.child(1).setText(1, str(trusted))
                self.child(1).setToolTip(1, self.child(1).text(1))

    class ConnectThread(QThread):
        """
        Thread for connecting a player to a server.
        """

        finished = pyqtSignal()

        def __init__(self, parent: "PlayersTab.AccountsTree", player: Player) -> None:
            super().__init__(parent)

            self.player = player

        def run(self) -> None:
            self.player.connect()
            self.finished.emit()  # No error message that we can get :(


from ..main import MainWindow
