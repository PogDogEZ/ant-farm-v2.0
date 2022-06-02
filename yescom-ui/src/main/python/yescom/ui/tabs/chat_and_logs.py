#!/usr/bin/env python3

from typing import List

from PyQt5.QtWidgets import *

from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.connection import Player

logger = Logging.getLogger("yescom.ui.tabs.chat_and_logs")


class ChatAndLogsTab(QWidget):
    """
    Shows either the server chat or YesCom logs.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.main_window = parent

        self._setup_tab()

        self.main_window.server_changed.connect(self._on_server_changed)

        self.main_window.player_added.connect(self._on_player_added)
        self.main_window.player_removed.connect(self._on_player_removed)

        self.main_window.player_chat.connect(self._on_player_chat)

        self._on_server_changed()

    def __repr__(self) -> str:
        return "<ChatAndLogsTab() at %x>" % id(self)

    # ------------------------------ Setup ------------------------------ #

    def _setup_tab(self) -> None:
        logger.finer("Setting up chat / logs tab...")
        main_layout = QVBoxLayout(self)

        self.tab_widget = QTabWidget(self)
        self.tab_widget.setTabPosition(QTabWidget.South)

        self._setup_chat_tab()
        self._setup_kick_logs_tab()
        self._setup_debug_logs_tab()

        main_layout.addWidget(self.tab_widget)

    def _setup_chat_tab(self) -> None:
        logger.finest("Setting up chat sub-tab...")

        self.chat_tab = QWidget(self)

        main_layout = QVBoxLayout(self.chat_tab)

        self.chat_browser = QTextBrowser(self.chat_tab)
        main_layout.addWidget(self.chat_browser)

        message_layout = QHBoxLayout()

        self.player_combo_box = QComboBox(self.chat_tab)
        self.player_combo_box.setFixedWidth(QApplication.fontMetrics().width("M" * 11))  # 16 is preferrable, but looks a bit much
        message_layout.addWidget(self.player_combo_box)

        self.chat_message_edit = QLineEdit(self.chat_tab)
        self.chat_message_edit.textChanged.connect(self._on_chat_text_changed)
        self.chat_message_edit.returnPressed.connect(self._on_chat_return_pressed)
        message_layout.addWidget(self.chat_message_edit)

        self.send_on_current_button = QPushButton(self.chat_tab)
        self.send_on_current_button.setText("Send on current")
        self.send_on_current_button.setEnabled(False)
        self.send_on_current_button.clicked.connect(self._on_send_on_current)
        message_layout.addWidget(self.send_on_current_button)

        self.send_on_all_button = QPushButton(self.chat_tab)
        self.send_on_all_button.setText("Send on all")
        self.send_on_all_button.setEnabled(False)
        self.send_on_all_button.clicked.connect(self._on_send_on_all)
        message_layout.addWidget(self.send_on_all_button)

        main_layout.addLayout(message_layout)

        self.tab_widget.addTab(self.chat_tab, "Server chat")

    def _setup_kick_logs_tab(self) -> None:
        ...

    def _setup_debug_logs_tab(self) -> None:
        ...

    # ------------------------------ Events ------------------------------ #

    def _on_server_changed(self) -> None:
        self.player_combo_box.clear()

        current = self.main_window.current_server
        if current is not None:
            for player in current.getPlayers():
                self._on_player_added(player)

    def _on_player_added(self, player: Player) -> None:
        if player.server == self.main_window.current_server:
            if self.player_combo_box.findData(player) < 0:
                self.player_combo_box.addItem(player.getUsername(), player)

    def _on_player_removed(self, player: Player) -> None:
        if player.server == self.main_window.current_server:
            index = self.player_combo_box.findData(player)
            if index >= 0:
                self.player_combo_box.removeItem(index)

    def _on_player_chat(self, player_chat: Emitters.PlayerChat) -> None:
        ...

    def _on_chat_text_changed(self, text: str) -> None:
        self.send_on_current_button.setEnabled(bool(text))
        self.send_on_all_button.setEnabled(bool(text))

    def _on_chat_return_pressed(self) -> None:
        self._on_send_on_current(False)

    def _on_send_on_current(self, checked: bool) -> None:
        if self.chat_message_edit.text():
            current = self.main_window.current_server
            if current is None:
                QMessageBox.warning(self, "Can't send", "Can't send chat message as no server is selected.")
                return
            elif not current.isConnected():
                QMessageBox.warning(self, "Can't send", "Can't send chat message as there is no connection to the server.")
                return

            player = self.player_combo_box.currentData()
            if player is None:
                QMessageBox.warning(self, "Can't send", "Can't send chat message as the selected player is not valid.")
                return
            elif not player.isConnected():
                QMessageBox.warning(self, "Can't send", "Can't send chat message as the selected player is not connected.")
                return

            player.chat(self.chat_message_edit.text())
            self.chat_message_edit.clear()

    def _on_send_on_all(self, checked: bool) -> None:
        if self.chat_message_edit.text():
            current = self.main_window.current_server
            if current is None:
                QMessageBox.warning(self, "Can't send", "Can't send chat message as no server is selected.")
                return
            elif not current.isConnected():
                QMessageBox.warning(self, "Can't send", "Can't send chat message as there is no connection to the server.")
                return

            can_send = []
            for player in current.getPlayers():
                if player.isConnected():
                    can_send.append(player)

            if len(can_send) > 1:
                message_box = QMessageBox(self)
                message_box.setIcon(QMessageBox.Warning)
                message_box.setWindowTitle("Send on all")
                message_box.setText("This will send a message on %i account(s)." % len(can_send))
                message_box.setInformativeText("I don't think I need to explain why this is a dumb idea.")
                message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
                message_box.setDefaultButton(QMessageBox.Cancel)
                message_box.setEscapeButton(QMessageBox.Cancel)
                message_box.accepted.connect(lambda: self._send_on_all(can_send))
                message_box.exec()
            else:
                self._send_on_all(can_send)

    def _send_on_all(self, players: List[Player]) -> None:
        """
        Sends the current chat message (in the edit box) on all the players provided.

        :param players: The players to send the message on.
        """

        if self.chat_message_edit.text():
            for player in players:
                player.chat(self.chat_message_edit.text())
            self.chat_message_edit.clear()


from ..main import MainWindow
