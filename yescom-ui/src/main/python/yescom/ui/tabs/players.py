#!/usr/bin/env python3

from typing import Tuple, Union

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ..widgets.players import AbstractPlayersTree, OfflinePlayersTree

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

        self._setup_tab()

        self.main_window.server_changed.connect(self._on_server_changed)
        self.main_window.connection_established.connect(self._on_server_changed)
        self.main_window.connection_lost.connect(self._on_server_changed)

        self.main_window.account_added.connect(self._on_account_added)
        self.main_window.account_error.connect(self._on_account_error)

    def __repr__(self) -> str:
        return "<PlayersTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        logger.finer("Setting up players tab...")

        main_layout = QHBoxLayout(self)

        accounts_layout = QVBoxLayout()

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
        self.password_edit.setEchoMode(QLineEdit.EchoMode.Password)
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
        self.disconnect_all_button.setText("Disconnect all")
        self.disconnect_all_button.setToolTip("Disconnects all currently online players and disables auto reconnecting.")
        self.disconnect_all_button.setEnabled(
            self.main_window.current_server is not None and self.main_window.current_server.isConnected()
        )
        self.disconnect_all_button.clicked.connect(lambda checked: self.main_window.disconnect_all())
        accounts_layout.addWidget(self.disconnect_all_button)

        main_layout.addLayout(accounts_layout)

        players_layout = QVBoxLayout()

        self.players_tree = OfflinePlayersTree(self)
        players_layout.addWidget(self.players_tree)

        # manage_players = QPushButton(self)
        # manage_players.setText("Manage players")
        # manage_players.clicked.connect(self._on_manage_players)
        # players_layout.addWidget(manage_players)

        main_layout.addLayout(players_layout)

    # ------------------------------ Events ------------------------------ #

    def _on_server_changed(self) -> None:
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

            QApplication.setOverrideCursor(Qt.CursorShape.ArrowCursor)

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

            QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
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

            QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
            self.yescom.accountHandler.addAccount(self._processing_account)

    # ------------------------------ Public methods ------------------------------ #

    def select_account(self, player: Player) -> None:
        """
        Selects the given player in the accounts list.

        :param player: The player to select.
        """

        for index in range(self.accounts_tree.topLevelItemCount()):
            top_level_item = self.accounts_tree.topLevelItem(index)
            if isinstance(top_level_item, PlayersTab.AccountItem) and top_level_item.player == player:
                self.accounts_tree.setCurrentIndex(self.accounts_tree.indexFromItem(top_level_item))
                return

    def expand_account(self, player: Player) -> None:
        """
        Expands the menu on the given player.

        :param player: The player whose menu to expand.
        """

        for index in range(self.accounts_tree.topLevelItemCount()):
            top_level_item = self.accounts_tree.topLevelItem(index)
            if isinstance(top_level_item, PlayersTab.AccountItem) and top_level_item.player == player:
                self.accounts_tree.expand(self.accounts_tree.indexFromItem(top_level_item))
                return

    # ------------------------------ Classes ------------------------------ #

    class AccountsTree(AbstractPlayersTree):
        """
        Stores players in a tree view.
        """

        def __init__(self, parent: "PlayersTab") -> None:
            super().__init__(parent, label="Accounts")

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self._connect_thread: Union[PlayersTab.ConnectThread, None] = None

            self.main_window.player_added.connect(self._on_player_added)
            self.main_window.player_removed.connect(self._on_player_removed)

            for server in self.yescom.servers:  # Players from accounts.txt will already exist by now, so need to add them
                for player in server.getPlayers():
                    self._on_player_added(player)

        # ------------------------------ Events ------------------------------ #

        def _on_player_added(self, player: Player) -> None:
            for index in range(self.topLevelItemCount()):
                top_level_item = self.topLevelItem(index)
                if isinstance(top_level_item, PlayersTab.AccountItem) and player == top_level_item.player:
                    return
            self.addTopLevelItem(PlayersTab.AccountItem(
                self, self.yescom.playersHandler.getInfo(player.getUUID(), player.getUsername()), player,
            ))
            self._update_count()

        def _on_player_removed(self, player: Player) -> None:
            for index in range(self.topLevelItemCount()):
                top_level_item = self.topLevelItem(index)
                if isinstance(top_level_item, PlayersTab.AccountItem) and player == top_level_item.player:
                    self.takeTopLevelItem(index)
                    break
            self._update_count()

        def _on_connect(self) -> None:
            player = self._connect_thread.player
            self._connect_thread = None
            QApplication.restoreOverrideCursor()

            if not player.isConnected():
                QMessageBox.warning(
                    self,
                    "Couldn't connect", "Couldn't connect player %r to %s:%i." % (
                        player.getUsername(), player.server.hostname, player.server.port,
                    ),
                )

    class AccountItem(AbstractPlayersTree.AbstractPlayerItem):
        """
        Information about an account / player that we "control".
        """

        # connected_tooltip = "%s\nServer: %s:%i\nConnected: %%s\nTPS: %%.1ftps\nPing: %%ims\nHealth: %%.1f\nRight click for more options."
        # disconnected_tooltip = "%s\nServer: %s:%i\nConnected: %%s\nRight click for more options."

        def __init__(self, parent: "PlayersTab.AccountsTree", info: PlayerInfo, player: Player):
            self.parent_ = parent  # Need to keep a reference of this ourselves, I guess, thanks Qt
            self.player = player

            super().__init__(parent, info)

            # self.connected_tooltip = self.connected_tooltip % (
            #     player.getUsername(), player.server.hostname, player.server.port,
            # )
            # self.disconnected_tooltip = self.disconnected_tooltip % (
            #     player.getUsername(), player.server.hostname, player.server.port,
            # )

            self._on_position_update(player)
            self._on_health_update(player)
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

        def _setup(self) -> None:
            self.connected_child = QTreeWidgetItem(self, ["Connected:"])
            # self.connected_child.setToolTip(0, "Is the player connected to the server?")
            self.addChild(self.connected_child)

            self.location_child = QTreeWidgetItem(self, ["Location"])
            self.position_child = QTreeWidgetItem(self.location_child, ["Position:"])
            self.position_child.setToolTip(0, "The current position of the player.")
            self.location_child.addChild(self.position_child)
            self.angle_child = QTreeWidgetItem(self.location_child, ["Angle:"])
            self.angle_child.setToolTip(0, "The current angle (yaw, pitch) of the player.")
            self.location_child.addChild(self.angle_child)
            self.dimension_child = QTreeWidgetItem(self.location_child, ["Dimension:"])
            self.dimension_child.setToolTip(0, "The dimension the player is currently in.")
            self.location_child.addChild(self.dimension_child)
            self.addChild(self.location_child)

            self.stats_child = QTreeWidgetItem(self, ["Stats"])
            self.health_child = QTreeWidgetItem(self.stats_child, ["Health:"])
            self.health_child.setToolTip(0, "The current health of the player.")
            self.stats_child.addChild(self.health_child)
            self.hunger_child = QTreeWidgetItem(self.stats_child, ["Hunger:"])
            self.hunger_child.setToolTip(0, "The current hunger level of the player.")
            self.stats_child.addChild(self.hunger_child)
            self.saturation_child = QTreeWidgetItem(self.stats_child, ["Saturation:"])
            self.saturation_child.setToolTip(0, "The current saturation level of the player.")
            self.stats_child.addChild(self.saturation_child)
            self.addChild(self.stats_child)

            self.debug_child = QTreeWidgetItem(self, ["Debug"])
            self.tickrate_child = QTreeWidgetItem(self.debug_child, ["Server tickrate:"])
            self.tickrate_child.setToolTip(0, "The current TPS that this player estimates the server is running at.")
            self.debug_child.addChild(self.tickrate_child)
            self.ping_child = QTreeWidgetItem(self.debug_child, ["Server ping:"])
            self.ping_child.setToolTip(0, "The ping that the server estimates this player has.")
            self.debug_child.addChild(self.ping_child)
            self.chunks_child = QTreeWidgetItem(self.debug_child, ["Chunks:"])
            self.chunks_child.setToolTip(0, "The number of chunks in the render distance of this player.")
            self.debug_child.addChild(self.chunks_child)
            self.addChild(self.debug_child)

        def apply_to_context_menu(self, context_menu: QMenu) -> None:
            context_menu.addAction("Manage account...")  # TODO: Managing accounts

            auto_reconnect = context_menu.addAction("Auto reconnect", lambda: self._toggle(self.player.AUTO_RECONNECT))
            auto_reconnect.setCheckable(True)
            auto_reconnect.setChecked(self.player.AUTO_RECONNECT.value)
            # auto_reconnect.setToolTip("Allow this player to automatically reconnect.")

            disable_auto_reconnect_on_auto_logout = context_menu.addAction(
                "Disable on autolog",
                lambda: self._toggle(self.player.DISABLE_AUTO_RECONNECT_ON_LOGOUT),
            )
            disable_auto_reconnect_on_auto_logout.setCheckable(True)
            disable_auto_reconnect_on_auto_logout.setChecked(self.player.DISABLE_AUTO_RECONNECT_ON_LOGOUT.value)

            context_menu.addSeparator()  # To make clear that the above are exclusive to each other

            connect = context_menu.addAction("Connect", self._connect)
            connect.setEnabled(self.parent_._connect_thread is None and not self.player.isConnected())
            # connect.setToolTip("Connects this player to the currently selected server.")

            disconnect = context_menu.addAction("Disconnect", lambda: self.player.disconnect("User disconnect"))
            disconnect.setEnabled(self.player.isConnected())
            # disconnect.setToolTip("Disconnects this player from the currently selected server.")

            remove = context_menu.addAction("Remove", self._remove)
            remove.setEnabled(self.player.server.hasPlayer(self.player))
            # remove.setEnabled(self.yescom.accountHandler.hasAccount(player.account))
            # remove.setToolTip("Removes this player's account from the account cache.")

            context_menu.addSeparator()

            context_menu.addAction("Copy username", lambda: QApplication.clipboard().setText(self.player.getUsername()))
            context_menu.addAction("Copy UUID", lambda: QApplication.clipboard().setText(str(self.player.getUUID())))

            context_menu.addSeparator()

            copy_coords = context_menu.addAction(
                "Copy coords",
                lambda: QApplication.clipboard().setText("%.1f, %.1f, %.1f" % (
                    self.player.getPosition().getX(),
                    self.player.getPosition().getY(),
                    self.player.getPosition().getZ(),
                )),
            )
            copy_coords.setEnabled(self.player.isConnected())
            copy_angle = context_menu.addAction(
                "Copy angle",
                lambda: QApplication.clipboard().setText("%.1f, %.1f" % (
                    self.player.getAngle().getYaw(),
                    self.player.getAngle().getPitch(),
                )),
            )
            copy_angle.setEnabled(self.player.isConnected())
            copy_dimension = context_menu.addAction(
                "Copy dimension",
                lambda: QApplication.clipboard().setText(str(self.player.getDimension()).lower()),
            )
            copy_dimension.setEnabled(self.player.isConnected())

            # TODO: Goto player, view disconnect logs, view chat

        # ------------------------------ Events ------------------------------ #

        def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
            if resolved[0] == self.player.getUUID():
                self.setIcon(0, QIcon(resolved[1]))

        def _on_server_change(self) -> None:
            self.setHidden(self.main_window.current_server != self.player.server)

        def _on_login(self, player: Player) -> None:  # Not strictly on login, who cares though
            if player == self.player:
                connected = player.isConnected()
                if connected:
                    # self.setToolTip(0, self.connected_tooltip % (
                    #     True, player.getServerTPS(), player.getServerPing(), player.getHealth(),
                    # ))
                    self.setForeground(0, QColor(*self.main_window.config.ONLINE_COLOUR.value))
                else:
                    # self.setToolTip(0, self.disconnected_tooltip % False)
                    self.setForeground(0, QColor(*self.main_window.config.OFFLINE_COLOUR.value))

                self.connected_child.setText(1, str(connected))
                self.connected_child.setToolTip(1, self.child(1).text(1))

                self.location_child.setHidden(not connected)
                self.position_child.setHidden(not connected)
                self.angle_child.setHidden(not connected)
                self.dimension_child.setHidden(not connected)

                self.stats_child.setHidden(not connected)
                self.health_child.setHidden(not connected)
                self.hunger_child.setHidden(not connected)
                self.saturation_child.setHidden(not connected)

                self.debug_child.setHidden(not connected)
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
                # self.setToolTip(0, self.connected_tooltip % (
                #     player.isConnected(), player.getServerTPS(), player.getServerPing(), player.getHealth(),
                # ))

                self.health_child.setText(1, "%.1f" % player.getHealth())
                self.health_child.setToolTip(1, self.health_child.text(1))
                self.hunger_child.setText(1, "%i" % player.getHunger())
                self.hunger_child.setToolTip(1, self.hunger_child.text(1))
                self.saturation_child.setText(1, "%.1f" % player.getSaturation())
                self.saturation_child.setToolTip(1, self.saturation_child.text(1))

        def _on_server_stats_update(self, player: Player) -> None:
            if player == self.player:
                # self.setToolTip(0, self.connected_tooltip % (
                #     player.isConnected(), player.getServerTPS(), player.getServerPing(), player.getHealth(),
                # ))

                self.tickrate_child.setText(1, "%.1ftps" % player.getServerTPS())
                self.tickrate_child.setToolTip(1, self.tickrate_child.text(1))
                self.ping_child.setText(1, "%ims" % player.getServerPing())
                self.ping_child.setToolTip(1, self.ping_child.text(1))
                self.chunks_child.setText(1, "%i" % len(player.loadedChunks))
                self.chunks_child.setToolTip(1, self.chunks_child.text(1))

        # ------------------------------ Utility methods ------------------------------ #

        def _toggle(self, option: Option) -> None:
            """
            Toggles a boolean option's value.
            """

            option.value = not option.value

        def _connect(self) -> None:
            """
            Connects a player to the currently selected server.
            """

            if self.parent_._connect_thread is None and not self.player.isConnected():
                QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
                self.parent_._connect_thread = PlayersTab.ConnectThread(self.parent_, self.player)
                self.parent_._connect_thread.finished.connect(self.parent_._on_connect)
                self.parent_._connect_thread.start()

        def _remove(self) -> None:
            """
            Removes a player's account from the cache and displays that it has been removed.
            """

            if self.yescom.accountHandler.hasAccount(self.player.account):
                self.yescom.accountHandler.removeAccount(self.player.account)

            if self.player.isConnected():
                self.player.disconnect("Removed by user")
            self.player.server.removePlayer(self.player)
            QMessageBox.information(self.parent_, "Success", "The player %r's account has been removed." % self.player.getUsername())

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
