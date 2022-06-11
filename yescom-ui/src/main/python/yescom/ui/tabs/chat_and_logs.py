#!/usr/bin/env python3

import datetime
import time
import webbrowser
from typing import Dict, List, Union

from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from ..dialogs.player_info import PlayerInfoDialog

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data.chat import (
    ChatMessage, DeathMessage, JoinLeaveMessage, PartyMessage, RegularMessage, WhisperMessage,
)
from ez.pogdog.yescom.api.data.player import PlayerInfo
from ez.pogdog.yescom.api.data.player.death import Death
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

        self.chat_browser = ChatAndLogsTab.ChatBrowser(self.chat_tab)
        main_layout.addWidget(self.chat_browser)

        message_layout = QHBoxLayout()

        self.player_combo_box = QComboBox(self.chat_tab)
        self.player_combo_box.currentIndexChanged.connect(self._on_player_selected)
        self.player_combo_box.setFixedWidth(QApplication.fontMetrics().width("M" * 11))  # 16 is preferrable, but looks a bit much
        message_layout.addWidget(self.player_combo_box)

        self.chat_message_edit = QLineEdit(self.chat_tab)
        self.chat_message_edit.setPlaceholderText("Type a chat message...")
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

    def _on_player_selected(self, index: int) -> None:
        self.chat_browser.current = self.player_combo_box.currentData()

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

    # ------------------------------ Classes ------------------------------ #

    class ChatBrowser(QTextEdit):
        """
        Displays nicely formatted chat messages.
        """

        @property
        def current(self) -> Player:
            return self._current

        @current.setter
        def current(self, value: Player) -> None:
            if value != self._current:
                self._current = value
                self.clear()

                if not value in self._chat_lines:
                    self._chat_lines[value] = []
                for chat_message in self._chat_lines[value]:
                    self._add_chat_message(chat_message)

        def __init__(self, parent: QWidget) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE
            self.config = self.main_window.config.chat

            # self.setAcceptRichText(True)
            self.setReadOnly(True)

            self._current: Union[Player, None] = None
            self._chat_lines: Dict[Player, List[Union[str, ChatMessage]]] = {}

            self.main_window.player_login.connect(self._on_player_login)
            self.main_window.player_logout.connect(self._on_player_logout)

            self.main_window.player_chat.connect(self._on_player_chat)  # TODO: Loading older messages

        # ------------------------------ Events ------------------------------ #

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            menu = self.createStandardContextMenu()
            clipboard = QApplication.clipboard()

            current = self.main_window.current_server
            if current is not None and self._current is not None:
                cursor: QTextCursor = self.cursorForPosition(event.pos())
                chat_message = self._chat_lines[self._current][cursor.blockNumber() - 1]
                logger.finer("Clicked chat message: %s" % chat_message)

                uuid: Union[UUID, None] = None
                ignore_option = False

                if chat_message.getClass() == DeathMessage:
                    uuid = chat_message.player
                elif chat_message.getClass() == JoinLeaveMessage:
                    uuid = chat_message.player
                elif chat_message.getClass() == PartyMessage:
                    uuid = chat_message.sender
                    ignore_option = True
                elif chat_message.getClass() == RegularMessage:
                    uuid = chat_message.sender
                    ignore_option = True
                elif chat_message.getClass() == WhisperMessage:
                    uuid = chat_message.recipient
                    ignore_option = True

                if uuid is not None:
                    menu.addSeparator()

                    # TODO: Whisper to option
                    # TODO: Ignore option
                    # if ignore_option:
                    #     menu.addAction("Ignore player")
                    # menu.addAction("Whisper to player")
                    # menu.addSeparator()

                    player = current.getPlayer(uuid)

                    view_player = menu.addAction("View account", lambda: self._view_account(player))
                    view_player.setEnabled(player is not None)
                    menu.addAction("View info...", lambda: self._view_info(self.yescom.playersHandler.getInfo(uuid)))

                    menu.addSeparator()
                    menu.addAction("Open NameMC...", lambda: webbrowser.open("https://namemc.com/profile/%s" % uuid))
                    menu.addSeparator()
                    menu.addAction(
                        "Copy username",
                        lambda: clipboard.setText(self.yescom.playersHandler.getName(uuid, "<unknown name>")),
                    )
                    menu.addAction("Copy UUID", lambda: clipboard.setText(str(uuid)))

            menu.exec(event.globalPos())

        def _on_player_login(self, player: Player) -> None:
            self._update_player(
                player, "%s connected to %s:%i." % (player.getUsername(), player.server.hostname, player.server.port),
            )

        def _on_player_logout(self, player_logout: Emitters.PlayerLogout) -> None:
            self._update_player(
                player_logout.player,
                "%s disconnected from %s:%i: %r" % (
                    player_logout.player.getUsername(),
                    player_logout.player.server.hostname,
                    player_logout.player.server.port,
                    player_logout.reason,
                ),
            )

        def _on_player_chat(self, player_chat: Emitters.PlayerChat) -> None:
            self._update_player(player_chat.server.getPlayer(player_chat.chatMessage.receiver), player_chat.chatMessage)

        # ------------------------------ Utility ------------------------------ #

        def _toggle_trusted(self, uuid: UUID) -> None:
            if self.yescom.playersHandler.isTrusted(uuid):
                self.yescom.playersHandler.removeTrusted(uuid)
            else:
                self.yescom.playersHandler.addTrusted(uuid)

        def _view_account(self, player: Player) -> None:
            if self.main_window.current_server != player.server:
                self.main_window.current_server = player.server

            self.main_window.players_tab.select_account(player)
            self.main_window.players_tab.expand_account(player)
            self.main_window.set_selected(self.main_window.players_tab)

        def _view_info(self, info: PlayerInfo) -> None:
            player_info_dialog = PlayerInfoDialog(self, info)
            player_info_dialog.show()

        def _update_player(self, player: Player, chat_message: Union[str, ChatMessage]) -> None:
            """
            Updates the information in the text browser, given a player and a new chat message they received.
            """

            if not player in self._chat_lines:
                self._chat_lines[player] = []
            self._chat_lines[player].append(chat_message)

            popped = 0
            while len(self._chat_lines[player]) > self.config.MAX_LINES.value:
                self._chat_lines[player].pop(0)
                popped += 1

            if player == self._current:
                previous_scroll = self.verticalScrollBar().sliderPosition()
                is_maximum = previous_scroll == self.verticalScrollBar().maximum()

                self._add_chat_message(chat_message)
                self._pop_chat_messages(popped)

                if is_maximum:
                    self.verticalScrollBar().setValue(self.verticalScrollBar().maximum())
                else:
                    self.verticalScrollBar().setValue(previous_scroll)

        def _add_chat_message(self, chat_message: Union[str, ChatMessage]) -> None:
            """
            Adds a chat message to this chat browser.

            :param chat_message: The chat message to add.
            """

            cursor = self.textCursor()
            # selection_before = cursor.selectionStart(), cursor.selectionEnd()
            position_before = cursor.position()

            cursor.clearSelection()
            cursor.movePosition(QTextCursor.End)
            cursor.insertBlock()

            if isinstance(chat_message, str):  # YesCom status message (kicked, connected)
                char_format = QTextCharFormat()
                char_format.setFontWeight(QFont.Bold)

                cursor.setCharFormat(char_format)
                cursor.insertText(chat_message)

            else:
                colours = {
                    "0": QColor(*self.config.BLACK_COLOUR.value),
                    "1": QColor(*self.config.DARK_BLUE_COLOUR.value),
                    "2": QColor(*self.config.DARK_GREEN_COLOUR.value),
                    "3": QColor(*self.config.DARK_AQUA_COLOUR.value),
                    "4": QColor(*self.config.DARK_RED_COLOUR.value),
                    "5": QColor(*self.config.DARK_PURPLE_COLOUR.value),
                    "6": QColor(*self.config.GOLD_COLOUR.value),
                    "7": QColor(*self.config.GRAY_COLOUR.value),
                    "8": QColor(*self.config.DARK_GRAY_COLOUR.value),
                    "9": QColor(*self.config.BLUE_COLOUR.value),
                    "a": QColor(*self.config.GREEN_COLOUR.value),
                    "b": QColor(*self.config.AQUA_COLOUR.value),
                    "c": QColor(*self.config.RED_COLOUR.value),
                    "d": QColor(*self.config.PURPLE_COLOUR.value),
                    "e": QColor(*self.config.YELLOW_COLOUR.value),
                    "f": QColor(*self.config.WHITE_COLOUR.value),
                }

                timestamp = str(datetime.datetime.fromtimestamp(chat_message.timestamp // 1000))
                # timedelta = str(datetime.timedelta(seconds=int(time.time()) - chat_message.timestamp // 1000,))

                if chat_message.getClass() == DeathMessage:
                    death = chat_message.death
                    tooltip = "Player %s death.\nType: %s\nKiller: %s\nTimestamp: %s\nRight click for more options." % (
                        self.yescom.playersHandler.getName(chat_message.player),
                        str(death.type).capitalize().replace("_", " "),
                        "None (or unknown)" if death.killer is None else self.yescom.playersHandler.getName(death.killer),
                        timestamp,  # timedelta,
                    )

                elif chat_message.getClass() == JoinLeaveMessage:
                    tooltip = "Player %s %s.\nTimestamp: %s\nRight click for more options." % (
                        self.yescom.playersHandler.getName(chat_message.player),
                        "joined" if chat_message.joining else "left",
                        timestamp,  # timedelta,
                    )
                elif chat_message.getClass() == PartyMessage:
                    tooltip = "Party message from %s.\nMessage: %r\nTimestamp: %s\nRight click for more options." % (
                        self.yescom.playersHandler.getName(chat_message.sender),
                        chat_message.actualMessage,
                        timestamp,  # timedelta,
                    )
                elif chat_message.getClass() == RegularMessage:
                    tooltip = "Chat message from %s.\nMessage: %r\nTimestamp: %s\nRight click for more options." % (
                        self.yescom.playersHandler.getName(chat_message.sender),
                        chat_message.actualMessage,
                        timestamp,  # timedelta,
                    )
                elif chat_message.getClass() == WhisperMessage:
                    tooltip = "Whisper %s %s.\nMessage: %r\nTimestamp: %s\nRight click for more options." % (
                        "to" if chat_message.sending else "from",
                        self.yescom.playersHandler.getName(chat_message.recipient),
                        chat_message.actualMessage,
                        timestamp,  # timedelta,
                    )
                else:
                    tooltip = "Timestamp: %s" % timestamp

                char_format = QTextCharFormat()
                if tooltip:
                    char_format.setToolTip(tooltip)

                cursor.setCharFormat(char_format)

                skip_first = not chat_message.message.startswith("§")
                message = chat_message.message.split("§")

                if skip_first:
                    cursor.insertText(message.pop(0))

                for part in message:
                    if not part:
                        continue
                    code, part = part[0], part[1:]

                    if code in colours:
                        char_format.setForeground(colours[code])
                    elif code == "l":  # Bold
                        char_format.setFontWeight(QFont.Bold)
                    elif code == "m":  # Strikethrough
                        char_format.setFontStrikeOut(True)
                    elif code == "n":  # Underline
                        char_format.setFontUnderline(True)
                    elif code == "o":  # Italic
                        char_format.setFontItalic(True)
                    elif code == "r":  # Reset
                        char_format = QTextCharFormat()
                        if tooltip:
                            char_format.setToolTip(tooltip)

                    cursor.setCharFormat(char_format)
                    cursor.insertText(part)

            # cursor.select(QTextCursor.)  # TODO: Redo selection
            cursor.setPosition(position_before)
            self.setTextCursor(cursor)

        def _pop_chat_messages(self, count: int) -> None:
            """
            Pops chat messages from the top of the chat history.

            :param count: The number of chat messages to pop.
            """

            if not count:
                return

            cursor = self.textCursor()
            position_before = cursor.position()

            cursor.movePosition(QTextCursor.Start)
            for index in range(count):
                cursor.select(QTextCursor.BlockUnderCursor)
                cursor.removeSelectedText()
                cursor.deleteChar()

            cursor.setPosition(position_before)
            self.setTextCursor(cursor)


from ..main import MainWindow
