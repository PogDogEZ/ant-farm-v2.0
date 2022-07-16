#!/usr/bin/env python3

from enum import Enum
from typing import Set, Tuple

import bresenham
from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *


class Selection:
    """
    A selection made by the user to query the state of chunks.
    """

    @property
    def empty(self) -> bool:
        """
        :return: Is the current selection empty?
        """

        return not self._positions

    @property
    def mode(self) -> "Selection.Mode":
        return self._mode

    @mode.setter
    def mode(self, value: "Selection.Mode") -> None:
        if value != self._mode:
            self._mode = value
            self.clear()

    def __init__(self, parent: QWidget, mode: "Selection.Mode") -> None:
        self._parent = parent
        self._mode = mode

        self._positions: Set[Tuple[int, int]] = set()
        # The group that all the seleciton items are stored in, it's much much faster to just delete this rather
        # than delete all the items in the group individually
        self._selection_group = QGraphicsItemGroup()
        self._selection_group.setZValue(8)
        self._parent._scene.addItem(self._selection_group)

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

        if self._mode == Selection.Mode.LINE:
            for point in bresenham.bresenham(old_x, old_z, new_x, new_z):
                if not point in self._positions:
                    rect = QGraphicsRectItem(point[0] * 16, point[1] * 16, 16, 16)
                    rect.setPen(self._selection_pen)
                    self._selection_group.addToGroup(rect)
                    self._positions.add(point)

        elif self._mode == Selection.Mode.BOX:
            if self._new_selection:
                self.clear()

            if not self._positions:
                rect = QGraphicsRectItem(old_x * 16, old_z * 16, 16, 16)
                rect.setPen(self._selection_pen)
                rect.setZValue(8)
                self._selection_group.addToGroup(rect)
                self._positions.add((old_x, old_z))

            rect = self._selection_group.childItems()[0]
            origin_x, origin_z = next(iter(self._positions))
            bounding_rect = rect.rect()  # Bruh
            bounding_rect.setBottomRight(QPointF(max(origin_x, new_x + 1) * 16, max(origin_z, new_z + 1) * 16))
            bounding_rect.setTopLeft(QPointF(min(origin_x, new_x) * 16, min(origin_z, new_z) * 16))
            rect.setRect(bounding_rect)

        self._new_selection = False

    def release(self) -> None:
        """
        Called when the mouse is released.
        """

        if self.mode == Selection.Mode.BOX:
            self._new_selection = True

    def clear(self) -> None:
        """
        Clears the current selection.
        """

        self._parent._scene.removeItem(self._selection_group)
        self._positions.clear()

        self._selection_group = QGraphicsItemGroup()  # Reinitialise group
        self._selection_group.setZValue(8)
        self._parent._scene.addItem(self._selection_group)

    def query(self) -> None:
        """
        Actually queries the selection.
        """

        if self.mode == Selection.Mode.LINE:
            ...  # positions = copy.copy(self._positions)

        elif self.mode == Selection.Mode.BOX:
            ...

        self.clear()

    # ------------------------------ Classes ------------------------------ #

    class Mode(Enum):
        """
        The current selection mode, chosen by the user.
        """

        NONE = 0
        LINE = 1
        BOX = 2
