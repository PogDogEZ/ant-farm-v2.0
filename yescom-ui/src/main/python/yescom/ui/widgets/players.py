#!/usr/bin/env python3

import datetime
import re
import webbrowser
from typing import Any, Tuple, Union

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from .shared import ContextMenuTree
from ..dialogs.players import PlayerInfoDialog

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data.player import PlayerInfo
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.connection import Player, Server

logger = Logging.getLogger("yescom.ui.widgets.players")


# class OnlinePlayersList(QWidget):
#     """
#     Displays a list of online players.
#     """
#
#     def __init__(self, parent: QWidget) -> None:
#         super().__init__(parent)
#
#         self.yescom = YesCom.getInstance()
#         self.main_window = MainWindow.INSTANCE
#
#         self._players: Dict[Tuple[PlayerInfo, Server], OnlinePlayersList.OnlinePlayerWidget] = {}
#
#         self._setup()
#
#         self.main_window.server_changed.connect(self._on_server_changed)
#
#         self.main_window.any_player_joined.connect(
#             lambda online_player_info: self._on_player_joined(online_player_info.info, online_player_info.server),
#         )
#         self.main_window.any_player_left.connect(
#             lambda online_player_info: self._on_player_left(online_player_info.info, online_player_info.server),
#         )
#
#         current = self.main_window.current_server
#         if current is not None:
#             for uuid in current.getOnlinePlayers().keySet():
#                 self._on_player_joined(self.yescom.playersHandler.getInfo(uuid), current)
#
#     def _setup(self) -> None:
#         logger.finer("Setting up online players list widget...")
#
#         main_layout = QVBoxLayout(self)
#
#         self.label = QLabel(self)
#         self.label.setText("Online players (0):")
#         main_layout.addWidget(self.label)
#
#         scroll_area = QScrollArea(self)
#         scroll_area.setWidgetResizable(True)
#         scroll_area.setVerticalScrollBarPolicy(Qt.ScrollBarAsNeeded)
#
#         self.scroll_widget = QWidget()
#         self.scroll_widget.setSizePolicy(QSizePolicy.Preferred, QSizePolicy.Maximum)
#         scroll_area.setWidget(self.scroll_widget)
#         self.scroll_layout = QVBoxLayout()
#         self.scroll_layout.setSpacing(2)
#         self.scroll_widget.setLayout(self.scroll_layout)
#
#         main_layout.addWidget(scroll_area)
#
#     # ------------------------------ Events ------------------------------ #
#
#     def _on_server_changed(self) -> None:
#         count = 0
#         if self.main_window.current_server is not None:
#             count = len(self.main_window.current_server.getOnlinePlayers())
#
#         self.label.setText("Online players (%i):" % count)
#
#     def _on_player_joined(self, info: PlayerInfo, server: Server) -> None:
#         self._on_player_left(info, server)  # Delete any previously existing ones, if necessary
#
#         widget = OnlinePlayersList.OnlinePlayerWidget(info, server)
#         self._players[info, server] = widget
#
#         self.scroll_layout.addWidget(widget)
#         self._on_server_changed()
#
#     def _on_player_left(self, info: PlayerInfo, server: Server) -> None:
#         if (info, server) in self._players:
#             self.scroll_layout.removeWidget(self._players[info, server])
#             del self._players[info, server]
#
#         self._on_server_changed()
#
#     # ------------------------------ Classes ------------------------------ #
#
#     class OnlinePlayerPopup(QWidget):
#         """
#         The menu that pops up when you left click on a plyer.
#         """
#
#         closed = pyqtSignal()
#
#         def __init__(self, parent: "OnlinePlayersList.OnlinePlayerWidget", origin: QPoint, info: PlayerInfo, server: Server) -> None:
#             super().__init__(parent)
#
#             self.yescom = YesCom.getInstance()
#             self.main_window = MainWindow.INSTANCE
#
#             self.info = info
#             self.server = server
#
#             geometry = self.geometry()
#             geometry.setTopLeft(origin)
#             self.setGeometry(geometry)
#             self.setWindowFlags(Qt.Window | Qt.Popup | Qt.FramelessWindowHint)
#
#             self._setup()
#
#             self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)
#             self.main_window.skin_downloader_thread.request_skin(info.uuid)
#
#             self.main_window.tick.connect(self._on_tick)
#
#             self.main_window.any_player_gamemode_update.connect(self._on_gamemode_update)
#             self.main_window.any_player_ping_update.connect(self._on_ping_update)
#
#         def _setup(self) -> None:
#             main_layout = QVBoxLayout(self)
#
#             self.skin_label = QLabel(self)
#             main_layout.addWidget(self.skin_label)
#
#             player_label = QLabel(self)
#             player_label.setText("Username: %s" % self.info.username)
#             player_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
#             main_layout.addWidget(player_label)
#
#             server_label = QLabel(self)
#             server_label.setText("Server: %s:%i" % (self.server.hostname, self.server.port))
#             server_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
#             main_layout.addWidget(server_label)
#
#             self.ping_label = QLabel(self)
#             self.ping_label.setText("Ping: %ims" % self.info.ping)
#             main_layout.addWidget(self.ping_label)
#
#             self.gamemode_label = QLabel(self)
#             self.gamemode_label.setText("Gamemode: %s" % str(self.info.gameMode).lower())
#             main_layout.addWidget(self.gamemode_label)
#
#             self.online_for_label = QLabel(self)
#             self.online_for_label.setText("Online for: %s" % str(datetime.timedelta(
#                 seconds=self.server.getSessionPlayTime(self.info.uuid) // 1000,
#             )))
#             main_layout.addWidget(self.online_for_label)
#
#             self.setLayout(main_layout)
#
#         # ------------------------------ Events ------------------------------ #
#
#         def closeEvent(self, event: QCloseEvent) -> None:
#             self.closed.emit()
#
#         def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
#             if resolved[0] == self.info.uuid:
#                 self.setWindowIcon(QIcon(resolved[1]))
#                 self.skin_label.setPixmap(resolved[1].scaled(64, 64, transformMode=Qt.FastTransformation))
#
#         def _on_tick(self) -> None:
#             self.online_for_label.setText("Online for: %s" % str(datetime.timedelta(
#                 seconds=self.server.getSessionPlayTime(self.info.uuid) // 1000,
#             )))
#
#         def _on_gamemode_update(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
#             if online_player_info.info == self.info and online_player_info.server == self.server:
#                 self.gamemode_label.setText("Gamemode: %s" % str(self.info.gameMode).lower())
#
#         def _on_ping_update(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
#             if online_player_info.info == self.info and online_player_info.server == self.server:
#                 self.ping_label.setText("Ping: %ims" % self.info.ping)
#
#     class OnlinePlayerWidget(QFrame):
#         """
#         The actual online player widget, displays the icon of the player and their name.
#         """
#
#         def __init__(self, info: PlayerInfo, server: Server) -> None:
#             super().__init__()
#
#             self.yescom = YesCom.getInstance()
#             self.main_window = MainWindow.INSTANCE
#
#             self.info = info
#             self.server = server
#
#             self._under_mouse = False
#
#             self._player_popup: Union[OnlinePlayersList.OnlinePlayerPopup, None] = None
#
#             known = server.hasPlayer(info.uuid)
#
#             self.setMouseTracking(True)
#             self.setAutoFillBackground(True)
#             self.setCursor(Qt.PointingHandCursor)
#             self.setSizePolicy(QSizePolicy.Minimum, QSizePolicy.Fixed)
#
#             self._setup()
#
#             self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)
#             self.main_window.skin_downloader_thread.request_skin(info.uuid)
#
#             self._on_trust_state_changed(info)
#
#             if known:
#                 font = self.font()
#                 font.setBold(True)
#                 self.setFont(font)
#
#             self.main_window.server_changed.connect(self._on_server_changed)
#             self.main_window.trust_state_changed.connect(self._on_trust_state_changed)
#
#         def _setup(self) -> None:
#             layout = QHBoxLayout(self)
#
#             self.icon_label = QLabel(self)
#             # self.icon_label.setAutoFillBackground(True)
#             layout.addWidget(self.icon_label)
#
#             self.username_label = QLabel(self)
#             # self.username_label.setAutoFillBackground(True)
#             self.username_label.setText(self.info.username)
#             layout.addWidget(self.username_label)
#
#             layout.setAlignment(Qt.AlignLeft)
#             layout.setContentsMargins(2, 2, 2, 2)
#
#         # ------------------------------ Events ------------------------------ #
#
#         def contextMenuEvent(self, event: QContextMenuEvent) -> None:
#             menu = QMenu()
#             clipboard = QApplication.clipboard()
#
#             player = self.server.getPlayer(self.info.uuid)
#
#             trusted = menu.addAction("Trusted", self._toggle_trusted)
#             trusted.setCheckable(True)
#             trusted.setChecked(self.yescom.playersHandler.isTrusted(self.info.uuid))
#             # trusted.setEnabled(player is None)  # Can't untrust players that we "own" <- uh, yeah we can, dumbass
#
#             menu.addSeparator()
#
#             view_player = menu.addAction("View account", lambda: self._view_account(player))
#             view_player.setEnabled(player is not None)
#             menu.addAction("View info...", self._view_info)
#
#             menu.addSeparator()
#             menu.addAction("Open NameMC...", lambda: webbrowser.open("https://namemc.com/profile/%s" % self.info.uuid))
#             menu.addSeparator()
#             menu.addAction("Copy username", lambda: clipboard.setText(self.info.username))
#             menu.addAction("Copy UUID", lambda: clipboard.setText(str(self.info.uuid)))
#
#             menu.exec(event.globalPos())
#
#         def enterEvent(self, event: QEvent) -> None:  # FIXME: Doesn't account for no focus
#             self.setCursor(Qt.PointingHandCursor)
#             dark = QApplication.palette().dark().color()
#             dark.setAlpha(dark.alpha() // 2)
#             self._set_background(dark)
#
#         def leaveEvent(self, event: QEvent) -> None:
#             self.setCursor(Qt.ArrowCursor)
#             if self._player_popup is None:
#                 self._reset_background()
#
#         def mousePressEvent(self, event: QMouseEvent) -> None:
#             if event.button() & Qt.LeftButton:
#                 dark = QApplication.palette().dark().color()
#                 dark.setAlpha(int(dark.alpha() * 0.75))
#                 self._set_background(dark)
#
#                 self._player_popup = OnlinePlayersList.OnlinePlayerPopup(self, event.globalPos(), self.info, self.server)
#                 self._player_popup.closed.connect(self._on_player_popup_closed)
#                 self._player_popup.show()
#
#         def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
#             if resolved[0] == self.info.uuid:
#                 self.icon_label.setPixmap(resolved[1])
#
#         def _on_tick(self) -> None:
#             current = self.main_window.current_server
#             if self.isExpanded() and current is not None:  # No need to update if we're not expanded
#                 self.online_for_child.setText(1, str(datetime.timedelta(
#                     seconds=current.getSessionPlayTime(self.info.uuid) // 1000,
#                 )))
#                 self.online_for_child.setToolTip(1, self.child(5).text(1))
#
#         def _on_server_changed(self) -> None:
#             self.setHidden(self.main_window.current_server != self.server)
#
#         def _on_trust_state_changed(self, info: PlayerInfo) -> None:
#             if info == self.info:
#                 trusted = self.yescom.playersHandler.isTrusted(info.uuid)
#                 palette = self.username_label.palette()
#                 if trusted:
#                     palette.setColor(self.username_label.foregroundRole(), QColor(0, 117, 163))
#                 else:
#                     palette.setColor(self.username_label.foregroundRole(), QApplication.palette().text().color())
#                 self.username_label.setPalette(palette)
#
#         def _on_player_popup_closed(self) -> None:
#             self._reset_background()
#             self._player_popup = None
#
#         # ------------------------------ Utility ------------------------------ #
#
#         def _set_background(self, colour: QColor) -> None:
#             palette = self.palette()
#             palette.setColor(self.backgroundRole(), colour)
#             self.setPalette(palette)
#
#         def _reset_background(self) -> None:
#             self._set_background(self.parent().palette().color(self.parent().backgroundRole()))
#
#         def _toggle_trusted(self) -> None:
#             if self.yescom.playersHandler.isTrusted(self.info.uuid):
#                 self.yescom.playersHandler.removeTrusted(self.info.uuid)
#             else:
#                 self.yescom.playersHandler.addTrusted(self.info.uuid)
#
#         def _view_account(self, player: Player) -> None:
#             if self.main_window.current_server != player.server:
#                 # Switch to the player's server if we're not viewing it, weird that we wouldn't be though
#                 self.main_window.current_server = player.server
#
#             self.main_window.players_tab.select_account(player)
#             self.main_window.players_tab.expand_account(player)
#             self.main_window.set_selected(self.main_window.players_tab)
#
#         def _view_info(self) -> None:
#             player_info_dialog = PlayerInfoDialog(self, self.info)
#             player_info_dialog.show()


class PlayerSelectionButton(QPushButton):
    """
    Allows the user to select a player.
    """

    @property
    def info(self) -> Union[PlayerInfo, None]:
        """
        :return: The currently selected player.
        """

        return self._info

    @info.setter
    def info(self, value: Union[PlayerInfo, None]) -> None:
        if value != self._info:
            self._info = value
            self._update()

    def __init__(self, parent: QWidget, info: Union[PlayerInfo, None] = None) -> None:
        super().__init__(parent)

        self.main_window = MainWindow.INSTANCE

        self._info: Union[PlayerInfo, None] = info
        self._selection_popup: Union[PlayerSelectionButton.SelectionPopup, None] = None

        self.clicked.connect(self._on_clicked)

        self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)

        self._update()

    def _update(self) -> None:
        if self._info is None:
            self.setText("Select a player...")
            self.setIcon(QIcon())
        else:
            self.setText(self._info.username)
            self.setIcon(QIcon())  # Reset icon and resolve the skin
            self.main_window.skin_downloader_thread.request_skin(self._info.uuid)

    # ------------------------------ Events ------------------------------ #

    def _on_clicked(self, checked: bool) -> None:
        if self._selection_popup is not None:
            self._selection_popup.close()
        self._selection_popup = PlayerSelectionButton.SelectionPopup(self, self.mapToGlobal(QPoint(0, 0)))
        self._selection_popup.show()

    def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
        if self._info is not None and resolved[0] == self._info.uuid:
            self.setIcon(QIcon(resolved[1]))

    # ------------------------------ Classes ------------------------------ #

    class SelectionPopup(QWidget):
        """
        The actual selection popup. Allows the user to search for the player.
        """

        def __init__(self, parent: "PlayerSelectionButton", origin: QPoint) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()

            geometry = self.geometry()
            geometry.setTopLeft(origin)
            self.setGeometry(geometry)
            self.setWindowFlags(Qt.WindowType.Window | Qt.WindowType.Popup | Qt.WindowType.FramelessWindowHint)

            self._setup()

            player_cache = self.yescom.playersHandler.getPlayerCache()
            with player_cache.synchronized():
                for info in player_cache.values():
                    self.players_list.addItem(info.username)

            # self.main_window.new_player_cached.connect(self._on_new_player_cached)

        def _setup(self) -> None:
            main_layout = QVBoxLayout(self)

            self.search_edit = QLineEdit(self)
            self.search_edit.setValidator(QRegularExpressionValidator(QRegularExpression("[A-Za-z0-9_]{1,18}"), self))
            self.search_edit.setFixedWidth(self.search_edit.fontMetrics().boundingRect("M" * 16).width())
            self.search_edit.setPlaceholderText("Search by username...")
            self.search_edit.setFocus()
            self.search_edit.textChanged.connect(self._on_search_text_changed)
            self.search_edit.returnPressed.connect(self._on_search_return_pressed)
            main_layout.addWidget(self.search_edit)

            self.players_list = QListWidget(self)
            self.players_list.setFixedHeight(self.fontMetrics().height() * 10)
            self.players_list.itemSelectionChanged.connect(self._on_player_selection_changed)
            self.players_list.itemDoubleClicked.connect(lambda item: self._select())
            main_layout.addWidget(self.players_list)

            buttons_layout = QHBoxLayout()

            self.deselect_button = QPushButton(self)
            self.deselect_button.setText("Deselect")
            self.deselect_button.clicked.connect(lambda checked: self._deselect())
            buttons_layout.addWidget(self.deselect_button)

            self.select_button = QPushButton(self)
            self.select_button.setText("Select")
            self.select_button.setEnabled(False)  # Won't have a selected player when it's first created
            self.select_button.clicked.connect(lambda checked: self._select())
            buttons_layout.addWidget(self.select_button)

            main_layout.addLayout(buttons_layout)
            main_layout.setContentsMargins(4, 4, 4, 4)

        # ------------------------------ Events ------------------------------ #

        def closeEvent(self, event: QCloseEvent) -> None:
            self.parent()._selection_dialog = None
            super().closeEvent(event)

        def _on_search_text_changed(self, text: str) -> None:
            text = text.lower()
            for index in range(self.players_list.count()):
                item = self.players_list.item(index)
                item.setHidden(not item.text().lower().startswith(text))

        def _on_search_return_pressed(self) -> None:
            current = self.players_list.currentItem()
            if current is None or current.isHidden():
                for index in range(self.players_list.count()):
                    item = self.players_list.item(index)
                    if not item.isHidden():
                        self.players_list.setCurrentItem(item)
                        break
                else:
                    ...  # TODO: Lookup name through Mojang API
            self._select()

        def _on_player_selection_changed(self) -> None:
            self.select_button.setEnabled(self.players_list.currentItem() is not None)

        # ------------------------------ Utility methods ------------------------------ #

        def _select(self) -> None:
            current = self.players_list.currentItem()
            if current is not None and not current.isHidden():
                self.parent().info = self.yescom.playersHandler.getInfo(current.text())
            self.close()

        def _deselect(self) -> None:
            self.parent().info = None
            self.close()


class AbstractPlayersTree(ContextMenuTree):
    """
    A tree widget containing information about players.
    """

    def __init__(self, parent: QWidget, label: str = "Players", show_skin: bool = True) -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE

        self._label = label
        self._show_skin = show_skin
        self._filter_popup: Union[AbstractPlayersTree.FilterPopup, None] = None

        self._setup()

        self.setColumnCount(2)  # Second column is for the data, first is for the labels
        self.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.setHeaderLabels(["%s (0):" % label, ""])

        header = self.header()
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)

    def _setup(self) -> None:
        ...

    # ------------------------------ Events ------------------------------ #

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if self._filter_popup is None and re.fullmatch("[A-Za-z0-9_]{1,18}", event.text()):
            self._filter_popup = AbstractPlayersTree.FilterPopup(self, self.mapToGlobal(QPoint(0, 0)), event.text())
            self._filter_popup.show()

        elif event.key() == Qt.Key.Key_Return:
            if isinstance(self.currentItem(), AbstractPlayersTree.AbstractPlayerItem):
                self.currentItem().on_user_selected()

    # def _on_item_double_clicked(self, item: QTreeWidgetItem, column: int) -> None:
    #     if isinstance(item, AbstractPlayersTree.AbstractPlayerItem):
    #         item.on_user_selected()

    # ------------------------------ Other methods ------------------------------ #

    def _update_count(self) -> None:
        """
        Updates the current item count.
        """

        count = 0
        for index in range(self.topLevelItemCount()):
            top_level_item = self.topLevelItem(index)
            if not top_level_item.isHidden():  # Children of this class may have hidden items for performance
                count += 1

        self.setHeaderLabels(["%s (%i):" % (self._label, count), ""])
        if self._filter_popup is not None:
            self._filter_popup.force_update()

    # ------------------------------ Classes ------------------------------ #

    class FilterPopup(QWidget):
        """
        Allows you to filter the players by certain things.
        """

        def __init__(self, parent: "AbstractPlayersTree", origin: QPoint, initial: str) -> None:
            super().__init__(parent)

            geometry = self.geometry()
            geometry.setTopLeft(origin)
            self.setGeometry(geometry)
            self.setWindowFlags(Qt.WindowType.Window | Qt.WindowType.Popup | Qt.WindowType.FramelessWindowHint)

            self._setup()

            self.search_edit.setText(initial)

        def _setup(self) -> None:
            main_layout = QVBoxLayout(self)

            self.search_edit = QLineEdit(self)
            self.search_edit.setValidator(QRegularExpressionValidator(QRegularExpression("[A-Za-z0-9_]{1,18}"), self))
            self.search_edit.setFixedWidth(self.search_edit.fontMetrics().boundingRect("M" * 16).width())
            self.search_edit.setPlaceholderText("Search by username...")
            self.search_edit.setFocus()
            self.search_edit.textChanged.connect(self._on_search_text_changed)
            self.search_edit.returnPressed.connect(self.close)
            main_layout.addWidget(self.search_edit)

            main_layout.setContentsMargins(0, 0, 0, 0)

            # TODO: Other filtering fields, play time, deaths, kills?

        # ------------------------------ Events ------------------------------ #

        def closeEvent(self, event: QCloseEvent) -> None:
            self.parent()._filter_popup = None
            for index in range(self.parent().topLevelItemCount()):
                top_level_item = self.parent().topLevelItem(index)
                if isinstance(top_level_item, AbstractPlayersTree.AbstractPlayerItem):
                    top_level_item.try_hide(False)

            if self.parent().currentItem() is not None:  # Scroll to the current item when we unhide everything
                self.parent().scrollToItem(self.parent().currentItem())

            self.parent()._update_count()

            super().closeEvent(event)

        def _on_search_text_changed(self, text: str) -> None:
            selected = False
            for index in range(self.parent().topLevelItemCount()):
                top_level_item = self.parent().topLevelItem(index)
                if isinstance(top_level_item, AbstractPlayersTree.AbstractPlayerItem):
                    valid = top_level_item.info.username.lower().startswith(text.lower())
                    if valid and not selected:  # Select the first one in the list
                        selected = True
                        self.parent().setCurrentItem(top_level_item)

                    top_level_item.try_hide(not valid)

        # ------------------------------ Other ------------------------------ #

        def force_update(self) -> None:
            """
            Forcefully udpate the state of the hidden players.
            """

            self._on_search_text_changed(self.search_edit.text())

    class AbstractPlayerItem(ContextMenuTree.ContextMenuTreeItem):
        """
        The actual item that displays the information about the player.
        """

        def __init__(self, parent: "AbstractPlayersTree", info: PlayerInfo) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.info = info

            uuid = info.uuid

            self.setText(0, info.username)

            uuid_child = QTreeWidgetItem(self, ["UUID:", str(uuid)])
            uuid_child.setToolTip(0, "The UUID of the player.")
            uuid_child.setToolTip(1, str(uuid))
            self.addChild(uuid_child)

            first_seen_child = QTreeWidgetItem(
                self, ["First seen:", str(datetime.datetime.fromtimestamp(info.firstSeen // 1000))],
            )
            first_seen_child.setToolTip(0, "The first time that YesCom saw the player (across all servers).")
            first_seen_child.setToolTip(1, first_seen_child.text(1))
            self.addChild(first_seen_child)

            self._setup()
            self._on_trust_state_changed(info)

            if parent._show_skin:
                self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)
                self.main_window.skin_downloader_thread.request_skin(uuid)
            self.main_window.trust_state_changed.connect(self._on_trust_state_changed)

        def __eq__(self, other: Any) -> bool:
            return isinstance(other, AbstractPlayersTree.AbstractPlayerItem) and other.info == self.info

        def _setup(self) -> None:
            ...

        def apply_to_context_menu(self, context_menu: QMenu, clicked_child: QTreeWidgetItem) -> None:
            """
            Applies this player to the context menu.

            :param context_menu: The context menu.
            :param clicked_child: The child item that the user right-clicked on.
            """

            player = None
            if self.main_window.current_server is not None:  # WTF?
                player = self.main_window.current_server.getPlayer(self.info.uuid)

            trusted = context_menu.addAction("Trusted", self._toggle_trusted)
            trusted.setCheckable(True)
            trusted.setChecked(self.yescom.playersHandler.isTrusted(self.info.uuid))
            # trusted.setEnabled(player is None)  # Can't untrust players that we "own" <- uh, yeah we can, dumbass

            context_menu.addSeparator()

            view_player = context_menu.addAction("View account", lambda: self._view_account(player))
            view_player.setEnabled(player is not None)
            context_menu.addAction("View info...", self._view_info)

            context_menu.addSeparator()
            context_menu.addAction(
                "Open NameMC...", lambda: webbrowser.open("https://namemc.com/profile/%s" % self.info.uuid),
            )
            context_menu.addSeparator()
            context_menu.addAction(
                "Copy username", lambda: QApplication.clipboard().setText(self.info.username),
            )
            context_menu.addAction("Copy UUID", lambda: QApplication.clipboard().setText(str(self.info.uuid)))

        def on_user_selected(self) -> None:
            """
            Called when the user presses return while this player is selected. By default it opens the info dialog 
            for the player.
            """

            self._view_info()

        def try_hide(self, hidden: bool) -> None:
            """
            Tries to hide this item, can be overriden in subclasses.

            :param hidden: Should this item be hidden?
            """

            self.setHidden(hidden)

        # ------------------------------ Events ------------------------------ #

        def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
            if resolved[0] == self.info.uuid:
                self.setIcon(0, QIcon(resolved[1]))

        def _on_trust_state_changed(self, info: PlayerInfo) -> None:
            if info == self.info:
                if self.yescom.playersHandler.isTrusted(self.info.uuid):
                    self.setForeground(0, QColor(*self.main_window.config.TRUSTED_COLOUR.value))
                else:
                    self.setForeground(0, QApplication.palette().text().color())

        # ------------------------------ Utility methods ------------------------------ #

        def _toggle_trusted(self) -> None:
            if self.yescom.playersHandler.isTrusted(self.info.uuid):
                self.yescom.playersHandler.removeTrusted(self.info.uuid)
            else:
                self.yescom.playersHandler.addTrusted(self.info.uuid)

        def _view_account(self, player: Player) -> None:
            if self.main_window.current_server != player.server:
                # Switch to the player's server if we're not viewing it, weird that we wouldn't be though
                self.main_window.current_server = player.server

            self.main_window.players_tab.select_account(player)
            self.main_window.players_tab.expand_account(player)
            self.main_window.set_selected(self.main_window.players_tab)

        def _view_info(self) -> None:
            player_info_dialog = PlayerInfoDialog(self.main_window, self.info)
            player_info_dialog.show()


class OnlinePlayersTree(AbstractPlayersTree):
    """
    Tree widget containing information about players currently online.
    """

    def __init__(self, parent: QWidget) -> None:
        super().__init__(parent, label="Online players")

        self.main_window.server_changed.connect(lambda: QTimer.singleShot(50, self._update_count))
        self.main_window.any_player_joined.connect(
            lambda online_player_info: self._on_player_joined(online_player_info.info, online_player_info.server),
        )
        self.main_window.any_player_left.connect(
            lambda online_player_info: self._on_player_left(online_player_info.info, online_player_info.server),
        )

        current = self.main_window.current_server
        if current is not None:
            for uuid in current.getOnlinePlayers().keySet():
                self._on_player_joined(self.yescom.playersHandler.getInfo(uuid), current)

    # ------------------------------ Events ------------------------------ #

    def _on_player_joined(self, info: PlayerInfo, server: Server) -> None:
        self._on_player_left(info, server)  # Delete any previously existing ones, if necessary
        self.addTopLevelItem(OnlinePlayersTree.OnlinePlayerItem(self, info, server))
        self._update_count()

    def _on_player_left(self, info: PlayerInfo, server: Server) -> None:
        for index in range(self.topLevelItemCount()):
            top_level_item = self.topLevelItem(index)
            if isinstance(top_level_item, OnlinePlayersTree.OnlinePlayerItem) and top_level_item.info == info and top_level_item.server == server:
                self.takeTopLevelItem(index)
                break
        self._update_count()

    # ------------------------------ Classes ------------------------------ #

    class OnlinePlayerItem(AbstractPlayersTree.AbstractPlayerItem):
        """
        Information about any player online on a server.
        """

        # tooltip = "%s\nServer: %s:%i.\nPing: %%ims\nGamemode: %%s\nRight click for more options."

        def __init__(self, parent: "OnlinePlayersTree", info: PlayerInfo, server: Server) -> None:
            self.server = server
            super().__init__(parent, info)

            # self.tooltip = self.tooltip % (name, server.hostname, server.port)
            # self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))
            if server.hasPlayer(info.uuid):
                font = self.font(0)
                font.setBold(True)
                self.setFont(0, font)

            self._on_server_changed()

            # TODO: More information, are they being tracked, etc...

            self.main_window.tick.connect(self._on_tick)
            self.main_window.server_changed.connect(self._on_server_changed)

            self.main_window.any_player_gamemode_update.connect(self._on_gamemode_update)
            self.main_window.any_player_ping_update.connect(self._on_ping_update)

        def __eq__(self, other: Any) -> bool:
            return isinstance(other, OnlinePlayersTree.OnlinePlayerItem) and other.info == self.info and other.server == self.server

        def _setup(self) -> None:
            self.online_for_child = QTreeWidgetItem(self, ["Online for:"])
            # self.online_for_child.setToolTip(0, "How long the player has been online for.")
            self.addChild(self.online_for_child)

            self.ping_child = QTreeWidgetItem(self, ["Ping:"])
            self.ping_child.setToolTip(0, "The latency that the server estimates this player has.")
            self.addChild(self.ping_child)
            self._on_ping_update(Emitters.OnlinePlayerInfo(self.info, self.server))

            self.gamemode_child = QTreeWidgetItem(self, ["Gamemode:"])
            self.gamemode_child.setToolTip(0, "The gamemode of the player.")
            self.addChild(self.gamemode_child)
            self._on_gamemode_update(Emitters.OnlinePlayerInfo(self.info, self.server))

        def try_hide(self, hidden: bool) -> None:
            self.setHidden(self.main_window.current_server == self.server and hidden)

        # ------------------------------ Events ------------------------------ #

        def _on_tick(self) -> None:
            current = self.main_window.current_server
            if self.isExpanded() and current is not None:  # No need to update if we're not expanded
                self.online_for_child.setText(1, str(datetime.timedelta(
                    seconds=current.getSessionPlayTime(self.info.uuid) // 1000,
                )))
                self.online_for_child.setToolTip(1, self.online_for_child.text(1))

        def _on_server_changed(self) -> None:
            self.setHidden(self.main_window.current_server != self.server)

        def _on_gamemode_update(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
            if online_player_info.info == self.info and online_player_info.server == self.server:
                info = online_player_info.info
                # self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))

                self.gamemode_child.setText(1, str(info.gameMode).lower())
                self.gamemode_child.setToolTip(1, str(info.gameMode).lower())

        def _on_ping_update(self, online_player_info: Emitters.OnlinePlayerInfo) -> None:
            if online_player_info.info == self.info and online_player_info.server == self.server:
                info = online_player_info.info
                # self.setToolTip(0, self.tooltip % (info.ping, str(info.gameMode).lower()))

                self.ping_child.setText(1, "%ims" % info.ping)
                self.ping_child.setToolTip(1, self.ping_child.text(1))


class OfflinePlayersTree(AbstractPlayersTree):
    """
    Tree widget containing information about all offline players that YesCom has seen.
    """

    def __init__(self, parent: QWidget) -> None:
        super().__init__(parent, label="All players", show_skin=False)

        header = self.header()
        header.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)  # Adding 900+ items really slows this down :(
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.Fixed)

        # Need to add the players that we've already cached
        player_cache = self.yescom.playersHandler.getPlayerCache()
        with player_cache.synchronized():
            for info in player_cache.values():
                self._on_new_player_cached(info, skip_check=True)  # No need to check for duplicates here

        header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        header.setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)

        self.main_window.new_player_cached.connect(self._on_new_player_cached)

    # ------------------------------ Events ------------------------------ #

    def _on_new_player_cached(self, info: PlayerInfo, skip_check: bool = False) -> None:
        if not skip_check:
            # Should hopefully happen less often as the number of players increases
            for index in range(self.topLevelItemCount()):
                top_level_item = self.topLevelItem(index)
                if isinstance(top_level_item, OfflinePlayersTree.OfflinePlayerItem) and top_level_item.info == info:
                    return
        self.addTopLevelItem(OfflinePlayersTree.OfflinePlayerItem(self, info))
        self._update_count()

    # ------------------------------ Classes ------------------------------ #

    class OfflinePlayerItem(AbstractPlayersTree.AbstractPlayerItem):
        """
        Information about any offline player that YesCom has seen.
        """

        def __init__(self, parent: "OfflinePlayersTree", info: PlayerInfo) -> None:
            super().__init__(parent, info)

        def __eq__(self, other: Any) -> bool:
            return isinstance(other, OfflinePlayersTree.OfflinePlayerItem) and other.info == self.info

        # def _setup(self) -> None:
        #     super()._setup()  # TODO: Sessions, are we tracking them, etc...

        def apply_to_context_menu(self, context_menu: QMenu, clicked_item: QTreeWidgetItem) -> None:
            trusted = context_menu.addAction("Trusted", self._toggle_trusted)
            trusted.setCheckable(True)
            trusted.setChecked(self.yescom.playersHandler.isTrusted(self.info.uuid))

            context_menu.addSeparator()
            context_menu.addAction("View info...", self._view_info)
            context_menu.addSeparator()
            context_menu.addAction(
                "Open NameMC...", lambda: webbrowser.open("https://namemc.com/profile/%s" % self.info.uuid),
            )
            context_menu.addSeparator()
            context_menu.addAction(
                "Copy username", lambda: QApplication.clipboard().setText(self.info.username),
            )
            context_menu.addAction("Copy UUID", lambda: QApplication.clipboard().setText(str(self.info.uuid)))


from ..main import MainWindow
