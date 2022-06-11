#!/usr/bin/env python3

import math
from enum import Enum
from typing import Dict, List, Tuple, Union

import bresenham
from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data import Dimension
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.core.connection import Player

logger = Logging.getLogger("yescom.ui.renderer.grid")


class GridRenderer(QGraphicsView):
    """
    Responsible for rendering the grid in the grid view tab.
    """

    @property
    def dimension(self) -> Dimension:
        return self._dimension

    @dimension.setter
    def dimension(self, value: Dimension) -> None:
        if value != self._dimension:
            center = self.mapToScene(self.width() // 2, self.height() // 2)

            if value == Dimension.NETHER:
                self.setSceneRect(self.sceneRect().translated(center / 8 - center))
                self._scale = (self._scale[0] * 8, self._scale[1] * 8)
                self.scale(8, 8)

            elif self._dimension == Dimension.NETHER:
                self._scale = (self._scale[0] * 0.125, self._scale[1] * 0.125)
                self.scale(0.125, 0.125)
                self.setSceneRect(self.sceneRect().translated(center * 8 - center))

            self._dimension = value
            self._update()

    @property
    def selection_mode(self) -> "GridRenderer.Selection.Mode":
        return self._selection.mode

    @selection_mode.setter
    def selection_mode(self, value: "GridRenderer.Selection.Mode") -> None:
        self._selection.mode = value

    def __init__(self, parent: QWidget) -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE
        self.config = self.main_window.config.renderer  # Direct reference cos it's more legible

        self.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarAlwaysOff)

        self.setTransformationAnchor(QGraphicsView.NoAnchor)
        self.setResizeAnchor(QGraphicsView.NoAnchor)

        self.setBackgroundBrush(QBrush(QColor(255, 255, 255)))  # TODO: Set based on dimension
        self.setFrameShape(QFrame.NoFrame)

        self._scale = (1, 1)
        self._dimension = Dimension.OVERWORLD

        self._last_mouse_pos = None

        self._scene = QGraphicsScene(self)
        self._scene.setSceneRect(-30000000, -30000000, 60000000, 60000000)

        self._setup_grid()
        self._setup_highways()
        self._setup_position()
        self._setup_scale()

        self._selection = GridRenderer.Selection(self, GridRenderer.Selection.Mode.NONE)
        self._regions: Dict[Tuple[int, int], QPixmap] = {}

        self._selecting = False

        self._do_render_grid = True
        self._do_render_position = True
        self._do_render_scale = True
        self._do_render_distances = True
        self._do_render_highways = True

        self.setSceneRect(-self.width() / 2, -self.height() / 2, self.width(), self.height())
        self.setScene(self._scene)

        self.main_window.player_added.connect(self._on_player_added)
        self.main_window.player_removed.connect(self._on_player_removed)

        if self.main_window.current_server is not None:
            for player in self.main_window.current_server.getPlayers():
                self._on_player_added(player)

        # FIXME: Update stuff initially (or continuously)

    # ------------------------------ Setting up ------------------------------ #

    def _setup_grid(self) -> None:
        grid_pen = QPen(QColor(*self.config.CHUNK_GRID_COLOUR.value))
        grid_pen.setWidth(1)
        grid_pen.setCosmetic(True)

        self._grid_x_lines: List[QGraphicsLineItem] = []
        self._grid_z_lines: List[QGraphicsLineItem] = []

        for index in range(-256, 256):
            line = self._scene.addLine(index * 16, -4096, index * 16, 4096, grid_pen)
            line.setZValue(4)
            self._grid_x_lines.append(line)

            line = self._scene.addLine(-4096, index * 16, 4096, index * 16, grid_pen)
            line.setZValue(4)
            self._grid_z_lines.append(line)

    def _setup_highways(self) -> None:
        highway_pen = QPen(QColor(*self.config.HIGHWAY_COLOUR.value))
        highway_pen.setWidth(4)
        highway_pen.setCosmetic(True)

        self._x_highway = self._scene.addLine(0, -30000000, 0, 30000000, highway_pen)
        self._x_highway.setZValue(7)
        self._z_highway = self._scene.addLine(-30000000, 0, 30000000, 0, highway_pen)
        self._z_highway.setZValue(7)

        highway_text_font = self.font()
        highway_text_font.setPointSize(highway_text_font.pointSize() - 2)

        self._plus_x_highway_text = self._scene.addText("+X", highway_text_font)
        self._plus_x_highway_text.setDefaultTextColor(QColor(*self.config.HIGHWAY_COLOUR.value, 200))
        self._plus_x_highway_text.setZValue(9)
        self._plus_x_highway_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)

        self._plus_z_highway_text = self._scene.addText("+Z", highway_text_font)
        self._plus_z_highway_text.setDefaultTextColor(QColor(*self.config.HIGHWAY_COLOUR.value, 200))
        self._plus_z_highway_text.setZValue(9)
        self._plus_z_highway_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)

        self._minus_x_highway_text = self._scene.addText("-X", highway_text_font)
        self._minus_x_highway_text.setDefaultTextColor(QColor(*self.config.HIGHWAY_COLOUR.value, 200))
        self._minus_x_highway_text.setZValue(9)
        self._minus_x_highway_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)

        self._minus_z_highway_text = self._scene.addText("-Z", highway_text_font)
        self._minus_z_highway_text.setDefaultTextColor(QColor(*self.config.HIGHWAY_COLOUR.value, 200))
        self._minus_z_highway_text.setZValue(9)
        self._minus_z_highway_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)

    def _setup_position(self) -> None:
        self._position_text = self._scene.addText("Position: 0, 0 (0, 0)")
        self._position_text.setZValue(10)
        self._position_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)
        # self._dimension_text = self._scene.addText("Dimension: overworld")
        # self._dimension_text.setZValue(10)
        # self._dimension_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)

    def _setup_scale(self) -> None:
        scale_line_pen = QPen(QColor(*self.config.SCALE_INDICATOR_COLOUR.value))
        scale_line_pen.setWidth(4)
        scale_line_pen.setCosmetic(True)

        self._scale_text = self._scene.addText("10k blocks")
        self._scale_text.setZValue(10)
        self._scale_text.setFlag(QGraphicsItem.ItemIgnoresTransformations)
        self._scale_lines: Tuple[QGraphicsLineItem, ...] = (
            self._scene.addLine(0, 0, 0, 0, scale_line_pen),
            self._scene.addLine(0, 0, 0, 0, scale_line_pen),
            self._scene.addLine(0, 0, 0, 0, scale_line_pen),
        )
        for line in self._scale_lines:
            line.setZValue(10)

    # ------------------------------ Events ------------------------------ #

    def resizeEvent(self, event: QResizeEvent) -> None:
        self._last_mouse_pos = None

        self._update()

    def mouseDoubleClickEvent(self, event: QMouseEvent) -> None:
        if self._selection.mode != GridRenderer.Selection.Mode.NONE:
            self._selecting = True
            self.setCursor(Qt.CrossCursor)

    def mouseMoveEvent(self, event: QMouseEvent) -> None:
        if event.buttons() & Qt.LeftButton:
            if QApplication.queryKeyboardModifiers() & Qt.ShiftModifier or QApplication.queryKeyboardModifiers() & Qt.ControlModifier:
                if self._selection.mode != GridRenderer.Selection.Mode.NONE and not self._selecting:
                    self._selecting = True
                    self.setCursor(Qt.CrossCursor)

            if self._selecting:
                if self._last_mouse_pos is not None:
                    old_position = self.mapToScene(self._last_mouse_pos)
                    new_position = self.mapToScene(event.pos())

                    self._selection.update(
                        math.floor(old_position.x() / 16), math.floor(old_position.y() / 16),
                        math.floor(new_position.x() / 16), math.floor(new_position.y() / 16),
                    )

                self._last_mouse_pos = event.pos()

            else:
                if self._last_mouse_pos is not None:
                    delta = self.mapToScene(self._last_mouse_pos) - self.mapToScene(event.pos())
                    self.setSceneRect(self.sceneRect().translated(delta))

                self.setCursor(Qt.ClosedHandCursor)
                self._last_mouse_pos = event.pos()

        self._update()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        if self._last_mouse_pos is not None:
            self.setCursor(Qt.ArrowCursor)
            self._last_mouse_pos = None

            self._selecting = False
            self._selection.release()

    def wheelEvent(self, event: QWheelEvent) -> None:
        # https://stackoverflow.com/questions/19113532/qgraphicsview-zooming-in-and-out-under-mouse-position-using-mouse-wheel
        old_pos = self.mapToScene(event.pos())
        self._scale = (  # Mfw I have to keep track of this myself
            self._scale[0] * math.exp(event.angleDelta().y() / self.config.SCALE_SENSITIVITY.value),  # TODO: Configurable
            self._scale[1] * math.exp(event.angleDelta().y() / self.config.SCALE_SENSITIVITY.value),
        )
        self.scale(
            math.exp(event.angleDelta().y() / self.config.SCALE_SENSITIVITY.value),
            math.exp(event.angleDelta().y() / self.config.SCALE_SENSITIVITY.value),
        )
        new_pos = self.mapToScene(event.pos())
        self.setSceneRect(self.sceneRect().translated(old_pos - new_pos))

        self._update()

    def _on_player_added(self, player: Player) -> None:
        ...  # self._scene.addItem(GridRenderer.PlayerItem(self, player))

    def _on_player_removed(self, player: Player) -> None:
        ...

    # ------------------------------ Updating ------------------------------ #

    def _update(self) -> None:
        """
        Updates the information on the screen.
        """

        self._update_grid()
        self._update_highways()

        self._update_position_information()
        self._update_scale_information()

    def _update_grid(self) -> None:
        """
        Updates the grid that is drawn.
        """

        min_pos = self.mapToScene(QPoint(0, 0))
        max_pos = self.mapToScene(QPoint(self.width(), self.height()))

        if self._do_render_grid:
            grid_size = (
                max(2 ** (math.floor((2 - math.log(self._scale[0], 2)) / 2) * 2), 1) * 16,
                max(2 ** (math.floor((2 - math.log(self._scale[1], 2)) / 2) * 2), 1) * 16,
            )

            x_lines = math.ceil(self.width() / (self._scale[0] * grid_size[0]) + 1)
            z_lines = math.ceil(self.height() / (self._scale[1] * grid_size[1]) + 1)

            x_delta = (min_pos.x() % grid_size[0]) / grid_size[0]
            z_delta = (min_pos.y() % grid_size[1]) / grid_size[1]

        else:
            grid_size = (0, 0)

            x_lines = 0
            z_lines = 0

            x_delta = 0
            z_delta = 0

        for index, line in enumerate(self._grid_x_lines):
            if index <= x_lines:
                line.setVisible(True)
                line.setLine(
                    min_pos.x() + (index - x_delta) * grid_size[0], min_pos.y(),
                    min_pos.x() + (index - x_delta) * grid_size[0], max_pos.y(),
                )
            else:
                line.setVisible(False)

        for index, line in enumerate(self._grid_z_lines):
            if index <= z_lines:
                line.setVisible(True)
                line.setLine(
                    min_pos.x(), min_pos.y() + (index - z_delta) * grid_size[1],
                    max_pos.x(), min_pos.y() + (index - z_delta) * grid_size[1],
                )
            else:
                line.setVisible(False)

    def _update_highways(self) -> None:
        """
        Updates the highways and their label positions.
        """

        self._x_highway.setVisible(self._do_render_highways)
        self._z_highway.setVisible(self._do_render_highways)

        self._plus_x_highway_text.setVisible(self._do_render_highways)
        self._plus_z_highway_text.setVisible(self._do_render_highways)
        self._minus_x_highway_text.setVisible(self._do_render_highways)
        self._minus_z_highway_text.setVisible(self._do_render_highways)

        if self._do_render_highways:
            min_pos = self.mapToScene(QPoint(0, 0))
            max_pos = self.mapToScene(QPoint(self.width(), self.height()))

            self._plus_x_highway_text.setVisible(max_pos.y() >= 0)
            self._plus_x_highway_text.setPos(
                0,  # -self._plus_x_highway_text.boundingRect().width() / 2 / self._scale[0],
                max_pos.y() - self._plus_x_highway_text.boundingRect().height() / self._scale[1],
            )
            self._plus_z_highway_text.setVisible(max_pos.x() >= 0)
            self._plus_z_highway_text.setPos(
                max_pos.x() - self._plus_z_highway_text.boundingRect().width() / self._scale[0],
                0,
            )
            self._minus_x_highway_text.setVisible(min_pos.y() <= 0)
            self._minus_x_highway_text.setPos(0, min_pos.y())
            self._minus_z_highway_text.setVisible(min_pos.x() <= 0)
            self._minus_z_highway_text.setPos(min_pos.x(), 0)

    def _update_position_information(self) -> None:
        """
        Updates the dimension and position information displayed in the top left corner.
        """

        self._position_text.setVisible(self._do_render_position)
        # self._dimension_text.setVisible(self._do_render_position)

        if self._do_render_position:
            self._position_text.setPos(self.mapToScene(QPoint(0, 0)))
            # self._dimension_text.setPos(self.mapToScene(QPoint(0, self.fontMetrics().height())))

            mouse_position = self.mapToScene(self.mapFromGlobal(QCursor.pos()))
            if self._dimension == Dimension.OVERWORLD:
                position_text = "Position: %.1f, %.1f (%.1f, %.1f)" % (
                    mouse_position.x(), mouse_position.y(), mouse_position.x() / 8, mouse_position.y() / 8,
                )
            elif self._dimension == Dimension.NETHER:
                position_text = "Position: %.1f, %.1f (%.1f, %.1f)" % (
                    mouse_position.x(), mouse_position.y(), mouse_position.x() * 8, mouse_position.y() * 8,
                )
            else:
                position_text = "Position: %.1f, %.1f" % (mouse_position.x(), mouse_position.y())

            self._position_text.setPlainText(position_text)
            # self._dimension_text.setPlainText("Dimension: " + str(self._dimension).lower())

    def _update_scale_information(self) -> None:
        """
        Updates the scale information displayed in the bottom left corner.
        """

        self._scale_text.setVisible(self._do_render_scale)
        for line in self._scale_lines:
            line.setVisible(self._do_render_scale)
        if self._do_render_scale:
            self._scale_text.setPos(self.mapToScene(QPoint(8, self.height() - self.fontMetrics().height() - 8)))

            chunks_on_screen = self.width() / (self._scale[0] * 16) + 1
            # High estimate for max width, better safe than sorry
            blocks = math.ceil(chunks_on_screen * (self.fontMetrics().width("MMMMM blocks") / self.width()) * 16)
            log_blocks = math.floor(math.log(blocks, 10))
            interval = math.ceil(blocks / (10 ** math.ceil(log_blocks)))

            # Intervals NOT taken from OpenStreetMap, I promise (not the 7, actually)
            blocks = min((2, 3, 5, 7, 10), key=lambda interval_: abs(interval_ - interval)) * (10 ** log_blocks)

            if blocks < 1000:
                text = "%i blocks" % blocks
            elif 1000 <= blocks < 1000000:
                text = "%ik blocks" % (blocks / 1000)
            else:  # elif 1000000 <= blocks:
                text = "%im blocks" % (blocks / 1000000)

            width = blocks * self._scale[0]
            self._scale_text.setPlainText(text)

            self._scale_lines[0].setLine(QLineF(
                self.mapToScene(QPoint(4, self.height() - 4)),
                self.mapToScene(QPoint(int(4 + width), self.height() - 4)),
            ))
            self._scale_lines[1].setLine(QLineF(
                self.mapToScene(QPoint(4, self.height() - 4)),
                self.mapToScene(QPoint(4, self.height() - self.fontMetrics().height())),
            ))
            self._scale_lines[2].setLine(QLineF(
                self.mapToScene(QPoint(int(4 + width), self.height() - 4)),
                self.mapToScene(QPoint(int(4 + width), self.height() - self.fontMetrics().height())),
            ))

    # ------------------------------ Classes ------------------------------ #

    class Selection:
        """
        A selection made by the user to query the state of chunks.
        """

        @property
        def mode(self) -> "GridRenderer.Selection.Mode":
            return self._mode

        @mode.setter
        def mode(self, value: "GridRenderer.Selection.Mode") -> None:
            if value != self._mode:
                self._mode = value
                self.clear()

        def __init__(self, parent: "GridRenderer", mode: "GridRenderer.Selection.Mode") -> None:
            self._parent = parent
            self._mode = mode

            self._positions: Dict[Tuple[int, int], QGraphicsRectItem] = {}
            self._selection_pen = QPen(QColor(*self._parent.config.SELECTION_COLOUR.value))
            self._selection_pen.setWidth(4)
            self._selection_pen.setCosmetic(True)

            self._new_selection = True

        def update(self, old_x: int, old_z: int, new_x: int, new_z: int) -> None:
            """
            Updates the selection given the old and new mouse positions.

            :param old_x: The old mouse X position (in terms of chunks).
            :param old_z: The old mouse Z position (in terms of chunks).
            :param new_x: The new mouse X position (in terms of chunks).
            :param new_z: The new mouse Z position (in terms of chunks).
            """

            if self._mode == GridRenderer.Selection.Mode.LINE:
                for point in bresenham.bresenham(old_x, old_z, new_x, new_z):
                    if not point in self._positions:
                        rect = QGraphicsRectItem(point[0] * 16, point[1] * 16, 16, 16)
                        rect.setPen(self._selection_pen)
                        rect.setZValue(8)
                        self._parent._scene.addItem(rect)
                        self._positions[point] = rect

            elif self._mode == GridRenderer.Selection.Mode.BOX:
                if self._new_selection:
                    self.clear()

                if not self._positions:
                    rect = QGraphicsRectItem(old_x * 16, old_z * 16, 16, 16)
                    rect.setPen(self._selection_pen)
                    rect.setZValue(8)
                    self._parent._scene.addItem(rect)
                    self._positions[old_x, old_z] = rect

                (origin_x, origin_z), rect = list(self._positions.items())[0]
                rect = rect.rect()  # Bruh
                rect.setBottomRight(QPoint(max(origin_x, new_x + 1) * 16, max(origin_z, new_z + 1) * 16))
                rect.setTopLeft(QPoint(min(origin_x, new_x) * 16, min(origin_z, new_z) * 16))
                self._positions[origin_x, origin_z].setRect(rect)

            self._new_selection = False

        def release(self) -> None:
            """
            Called when the mouse is released.
            """

            if self.mode == GridRenderer.Selection.Mode.BOX:
                self._new_selection = True

        def clear(self) -> None:
            """
            Clears the current selection.
            """

            for position in self._positions.values():
                self._parent._scene.removeItem(position)
            self._positions.clear()

        class Mode(Enum):
            """
            The current selection mode, chosen by the user.
            """

            NONE = 0
            LINE = 1
            BOX = 2

    class PlayerItem(QGraphicsItem):
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
            painter.setRenderHint(QPainter.Antialiasing)
            if self._player.isConnected():
                painter.setBrush(QBrush(QColor(0, 255, 0)))
            else:
                painter.setBrush(QBrush(QColor(255, 0, 0)))

            painter.drawEllipse(0, 0, 64, 64)

        # ------------------------------ Events ------------------------------ #

        def hoverEnterEvent(self, event: QGraphicsSceneHoverEvent) -> None:
            self._parent.setCursor(Qt.PointingHandCursor)

        def hoverLeaveEvent(self, event: QGraphicsSceneHoverEvent) -> None:
            self._parent.setCursor(Qt.ArrowCursor)

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


from ..main import MainWindow
