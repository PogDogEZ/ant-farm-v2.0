#!/usr/bin/env python3

from typing import Dict

from PyQt6.QtWidgets import *

from ez.pogdog.yescom.core.config import IConfig, Option


class OptionsItem(QTreeWidgetItem):
    """
    A tree widget child that allows you to manage options.
    """

    def __init__(self, parent: QTreeWidgetItem, config: IConfig) -> None:
        super().__init__(parent)

        self.main_window = MainWindow.INSTANCE

        self.config = config
        self.options = config.getOptions()

        self.setText(0, "Options (%i)" % len(self.options))

        self.children: Dict[Option, QTreeWidgetItem] = {}
        for option in self.options:
            text = str(option.value)
            option_child = QTreeWidgetItem(self, [option.name + ":", text])
            option_child.setToolTip(0, option.description)
            option_child.setToolTip(1, text)
            self.addChild(option_child)
            self.children[option] = option_child

        self._on_tick(force=True)

        self.main_window.tick.connect(self._on_tick)

    def _on_tick(self, force: bool = False) -> None:
        self.setHidden(not self.options)

        if (self.parent().isExpanded() and self.isExpanded()) or force:
            for option, child in self.children.items():
                text = str(option.value)
                child.setText(1, text)
                child.setToolTip(1, text)


from ..main import MainWindow
