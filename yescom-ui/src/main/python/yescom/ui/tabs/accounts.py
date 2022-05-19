#!/usr/bin/env python3

from typing import Union

from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.account import IAccount
from ez.pogdog.yescom.core.account.accounts import Microsoft, Mojang
from ez.pogdog.yescom.core.config import Option
from ez.pogdog.yescom.core.connection import Player

logger = Logging.getLogger("yescom.ui.tabs.accounts")


class AccountsTab(QTabWidget):
    """
    Allows you to manage accounts that are associated with YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE

        self._processing_account: Union[IAccount, None] = None

        parent.server_changed.connect(self._on_server_changed)
        parent.connection_established.connect(self._on_server_changed)
        parent.connection_lost.connect(self._on_server_changed)

        parent.account_added.connect(self._on_account_added)
        parent.account_error.connect(self._on_account_error)

        parent.player_added.connect(self._on_player_added)
        parent.player_removed.connect(self._on_player_removed)

        self._setup_tab()

        for server in self.yescom.servers:  # Players from accounts.txt will already exist by now, so need to add them
            for player in server.getPlayers():
                self._on_player_added(player)

    def __repr__(self) -> str:
        return "<AccountsTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        main_layout = QHBoxLayout(self)

        list_layout = QVBoxLayout()

        self.accounts_label = QLabel(self)
        self.accounts_label.setText("Accounts (0):")
        list_layout.addWidget(self.accounts_label)

        self.accounts_tree = AccountsTab.PlayersTree(self)
        list_layout.addWidget(self.accounts_tree)

        self.disconnect_all_button = QPushButton(self)
        self.disconnect_all_button.setEnabled(
            self.main_window.current_server is not None and self.main_window.current_server.isConnected()
        )
        self.disconnect_all_button.setText("Disconnect all")
        self.disconnect_all_button.setToolTip("Disconnects all currently online players and disables auto reconnecting.")
        self.disconnect_all_button.clicked.connect(lambda checked: self.main_window.disconnect_all())
        list_layout.addWidget(self.disconnect_all_button)

        main_layout.addLayout(list_layout)

        login_layout = QVBoxLayout()

        login_label = QLabel(self)
        login_label.setText("Log in new account:")
        login_layout.addWidget(login_label)

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

        login_layout.addLayout(credentials_layout)

        self.mojang_login_button = QPushButton(self)
        self.mojang_login_button.setText("Mojang login")
        self.mojang_login_button.setEnabled(False)
        self.mojang_login_button.clicked.connect(self._on_mojang_login)
        login_layout.addWidget(self.mojang_login_button)

        self.microsoft_login_button = QPushButton(self)
        self.microsoft_login_button.setText("Microsoft login")
        self.microsoft_login_button.setEnabled(False)
        self.microsoft_login_button.clicked.connect(self._on_microsoft_login)
        login_layout.addWidget(self.microsoft_login_button)

        login_layout.addItem(QSpacerItem(20, 40, QSizePolicy.Minimum, QSizePolicy.Expanding))

        main_layout.addLayout(login_layout)

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
        self.accounts_tree.addTopLevelItem(AccountsTab.PlayerItem(self.accounts_tree, player))

    def _on_player_removed(self, player: Player) -> None:
        for top_level_index in range(self.accounts_tree.topLevelItemCount()):
            if player == self.accounts_tree.topLevelItem(top_level_index).player:
                self._on_server_changed()
                self.accounts_tree.removeItemWidget(self.accounts_tree.topLevelItem(top_level_index), 0)
                return

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

    def select(self, player: Player) -> None:
        """
        Selects the given player in the accounts list.

        :param player: The player to select.
        """

        for top_level_index in range(self.accounts_tree.topLevelItemCount()):
            item = self.accounts_tree.topLevelItem(top_level_index)
            if item.player == player:
                self.accounts_tree.setCurrentIndex(self.accounts_tree.indexFromItem(item))
                return

    def expand(self, player: Player) -> None:
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

    class PlayersTree(QTreeWidget):
        """
        Stores players in a tree view.
        """

        def __init__(self, parent: "AccountsTab") -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.setHeaderHidden(True)

            self._connect_thread: Union[AccountsTab.ConnectThread, None] = None

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            current = self.currentItem()
            if current is None:
                return

            menu = QMenu()
            clipboard = QApplication.clipboard()

            if isinstance(current, AccountsTab.PlayerItem):
                player = current.player
            elif isinstance(current.parent(), AccountsTab.PlayerItem):
                player = current.parent().player
            else:
                logger.warning("Don't know how to handle item %s." % current)
                return

            # TODO: Proper options integration?
            auto_reconnect = menu.addAction("Auto reconnect", lambda: self._toggle(player.AUTO_RECONNECT))
            auto_reconnect.setCheckable(True)
            auto_reconnect.setChecked(player.AUTO_RECONNECT.value)
            # auto_reconnect.setToolTip("Allow this player to automatically reconnect.")

            connect = menu.addAction("Connect", lambda: self._connect(player))
            connect.setEnabled(self._connect_thread is None and not player.isConnected())
            # connect.setToolTip("Connects this player to the currently selected server.")

            disconnect = menu.addAction("Disconnect", lambda: player.disconnect("User disconnect"))
            disconnect.setEnabled(player.isConnected())
            # disconnect.setToolTip("Disconnects this player from the currently selected server.")

            # TODO: Remove account is kinda useless

            remove = menu.addAction("Remove account", lambda: self._remove(player))
            remove.setEnabled(self.yescom.accountHandler.hasAccount(player.account))
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

        # ------------------------------ Events ------------------------------ #

        def _on_connect(self) -> None:
            player = self._connect_thread.player
            self._connect_thread = None
            QApplication.restoreOverrideCursor()

            if not player.isConnected():
                QMessageBox.warning(self, "Couldn't connect", "Couldn't connect player %r to %s:%i." %
                                          (player.getUsername(), player.server.hostname, player.server.port))

        # ------------------------------ Utility methods ------------------------------ #

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
                self._connect_thread = AccountsTab.ConnectThread(self, player)
                self._connect_thread.finished.connect(self._on_connect)
                self._connect_thread.start()

        def _remove(self, player: Player) -> None:
            """
            Removes a player's account from the cache and displays that it has been removed.
            """

            if self.yescom.accountHandler.hasAccount(player.account):
                self.yescom.accountHandler.removeAccount(player.account)
                QMessageBox.information(self, "Success", "The player %r's account has been removed." % player.getUsername())

    class PlayerItem(QTreeWidgetItem):
        """
        Information about a player that we "control".
        """

        tooltip = "%s\nServer: %s:%i\nConnected: %%s\nTPS: %%.1ftps\nPing: %%ims\nHealth: %%.1f\nRight click for more options."

        def __init__(self, parent: "AccountsTab.PlayersTree", player: Player):
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.player = player

            self.tooltip = self.tooltip % (player.getUsername(), player.server.hostname, player.server.port)
            self.setText(0, player.getUsername())  # TODO: Change colour if connected or not
            self.setToolTip(0, self.tooltip % (False, 20, 0, 20))
            self.main_window.skin_downloader_thread.request_skin(player.getUUID(), lambda icon: self.setIcon(0, icon))

            for index in range(11):
                self.addChild(QTreeWidgetItem(self, []))

            self.child(0).setText(0, "UUID: %s" % self.player.getUUID())
            self.child(0).setToolTip(0, "The player's UUID.")

            self._on_login(player)
            self._on_position_update(player)
            self._on_health_update(player)
            self._on_server_stats_update(player)

            self.child(1).setToolTip(0, "Is the player connected to the server?")

            self.child(2).setToolTip(0, "The current position of the player.")
            self.child(3).setToolTip(0, "The current angle (yaw, pitch) of the player.")
            self.child(4).setToolTip(0, "The dimension the player is currently in.")

            self.child(5).setToolTip(0, "The current health of the player.")
            self.child(6).setToolTip(0, "The current hunger level of the player.")
            self.child(7).setToolTip(0, "The current saturation level of the player.")

            self.child(8).setToolTip(0, "The current TPS that this player estimates the server is running at.")
            self.child(9).setToolTip(0, "The ping that the server estimates this player has.")
            self.child(10).setToolTip(0, "The number of chunks in the render distance of this player.")

            # TODO: Last position if logged out
            # TODO: Failed connection attempts

            self.main_window.server_changed.connect(self._on_server_change)

            self.main_window.player_login.connect(self._on_login)
            self.main_window.player_logout.connect(self._on_logout)

            self.main_window.player_position_update.connect(self._on_position_update)
            self.main_window.player_health_update.connect(self._on_health_update)
            self.main_window.player_server_stats_update.connect(self._on_server_stats_update)

        def __eq__(self, other) -> bool:
            return isinstance(other, AccountsTab.PlayerItem) and other.player == self.player

        def _on_server_change(self) -> None:
            self.setHidden(self.main_window.current_server != self.player.server)

        def _on_login(self, player: Player) -> None:
            if player == self.player:
                self.setToolTip(0, self.tooltip % (player.isConnected(), player.getServerTPS(), player.getServerPing(),
                                                   player.getHealth()))
                self.child(1).setText(0, "Connected: %s" % player.isConnected())

                self._on_position_update(player)
                self._on_health_update(player)
                self._on_server_stats_update(player)

        def _on_logout(self, player_logout: Emitters.PlayerLogout) -> None:
            self._on_login(player_logout.player)

        def _on_position_update(self, player: Player) -> None:
            if player == self.player:
                position = player.getPosition()  # Local lookups are so much faster in Python
                angle = player.getAngle()

                self.child(2).setHidden(not player.isConnected())
                self.child(2).setText(0, "Position: %.1f, %.1f, %.1f" % (position.getX(), position.getY(), position.getZ()))
                self.child(3).setHidden(not player.isConnected())
                self.child(3).setText(0, "Angle: %.1f, %.1f" % (angle.getYaw(), angle.getPitch()))
                self.child(4).setHidden(not player.isConnected())
                self.child(4).setText(0, "Dimension: %s" % str(player.getDimension()).lower())

        def _on_health_update(self, player: Player) -> None:
            if player == self.player:
                self.setToolTip(0, self.tooltip % (player.isConnected(), player.getServerTPS(), player.getServerPing(),
                                                   player.getHealth()))

                self.child(5).setHidden(not player.isConnected())
                self.child(5).setText(0, "Health: %.1f" % player.getHealth())
                self.child(6).setHidden(not player.isConnected())
                self.child(6).setText(0, "Hunger: %i" % player.getHunger())
                self.child(7).setHidden(not player.isConnected())
                self.child(7).setText(0, "Saturation: %.1f" % player.getSaturation())

        def _on_server_stats_update(self, player: Player) -> None:
            if player == self.player:
                self.setToolTip(0, self.tooltip % (player.isConnected(), player.getServerTPS(), player.getServerPing(),
                                                   player.getHealth()))

                self.child(8).setHidden(not player.isConnected())
                self.child(8).setText(0, "Server tickrate: %.1ftps" % player.getServerTPS())
                self.child(9).setHidden(not player.isConnected())
                self.child(9).setText(0, "Server ping: %ims" % player.getServerPing())
                self.child(10).setHidden(not player.isConnected())
                self.child(10).setText(0, "Chunks: %i (render distance %i)" %
                                          (len(player.loadedChunks), (len(player.loadedChunks) ** .5 - 1) // 2))

    class ConnectThread(QThread):
        """
        Thread for connecting a player to a server.
        """

        finished = pyqtSignal()

        def __init__(self, parent: "AccountsTab.PlayersTree", player: Player) -> None:
            super().__init__(parent)

            self.player = player

        def run(self) -> None:
            self.player.connect()
            self.finished.emit()  # No error message that we can get :(


from ..main import MainWindow
