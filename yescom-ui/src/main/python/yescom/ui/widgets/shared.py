#!/usr/bin/env python3

from abc import abstractmethod

from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ez.pogdog.yescom.api import Logging

logger = Logging.getLogger("yescom.ui.widgets.shared")


class ContextMenuTree(QTreeWidget):
    """
    A tree widget that is compatible with the ContextMenuTreeItem.
    """

    def contextMenuEvent(self, event: QContextMenuEvent) -> None:
        current = self.currentItem()
        if current is None:
            return

        menu = QMenu()

        applied = False
        while current is not None:  # Should be in order from the topmost to the bottommost
            if isinstance(current, ContextMenuTree.ContextMenuTreeItem):
                current.apply_to_context_menu(menu, current)
                menu.addSeparator()
                applied = True
            current = current.parent()
        if not applied:
            logger.warning("Don't know how to handle item %s." % current)
            return

        menu.exec(event.globalPos())

    class ContextMenuTreeItem(QTreeWidgetItem):
        """
        Abstract class which indicates that the tree widget item can apply itself to a context menu.
        """

        @abstractmethod
        def apply_to_context_menu(self, context_menu: QMenu, clicked_child: QTreeWidgetItem) -> None:
            """
            Applies this player to the context menu.

            :param context_menu: The context menu.
            :param clicked_child: The child item that the user right-clicked on.
            """
