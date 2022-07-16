#!/usr/bin/env python3

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.connection import Player


class PlayerItem(QGraphicsItemGroup):
    """
    A player that we know about.
    """

    def __init__(self, parent: "GridRenderer", player: Player) -> None:
        super().__init__(None)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE

        self.setAcceptHoverEvents(True)
        # self.setFlags(QGraphicsItem.ItemIgnoresTransformations | QGraphicsItem.ItemIsSelectable)

        self._parent = parent
        self._player = player

        self.main_window.player_login.connect(self._on_player_login)
        self.main_window.player_logout.connect(self._on_player_logout)

        self.main_window.player_position_update.connect(self._on_player_position_update)
        self.main_window.player_health_update.connect(self._on_player_health_update)

    def boundingRect(self) -> QRectF:
        return QRectF(-16, -16, 32, 32)

    def paint(self, painter: QPainter, option: QStyleOptionGraphicsItem, widget: QWidget = None) -> None:
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        if self._player.isConnected():
            painter.setBrush(QBrush(QColor(0, 255, 0)))
        else:
            painter.setBrush(QBrush(QColor(255, 0, 0)))

        painter.drawEllipse(0, 0, 64, 64)

    # ------------------------------ Events ------------------------------ #

    def hoverEnterEvent(self, event: QGraphicsSceneHoverEvent) -> None:
        ...  # self.main_window.grid_view_tab.setCursor(Qt.CursorShape.PointingHandCursor)

    def hoverLeaveEvent(self, event: QGraphicsSceneHoverEvent) -> None:
        ...  # self.main_window.grid_view_tab.unsetCursor()

    def _on_player_login(self, player: Player) -> None:
        if player == self._player:
            self._connected = True

    def _on_player_logout(self, player_logout: Emitters.PlayerLogout) -> None:
        if player_logout.player == self._player:
            self._connected = False

    def _on_player_position_update(self, player: Player) -> None:
        if player == self._player:
            self.setPos(player.getPosition().getX(), player.getPosition().getZ())
            self._dimension = player.getDimension()

    def _on_player_health_update(self, player: Player) -> None:
        ...


from . import GridRenderer
from ...main import MainWindow
