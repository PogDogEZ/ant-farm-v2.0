#!/usr/bin/env python3

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ..resources import Resources

from ez.pogdog.yescom.api.data import Dimension


class DimensionComboBox(QComboBox):
    """
    Allows the user to select a Minecraft dimension.
    """

    _ICONS = {
        Dimension.OVERWORLD: "icons/Grass_Block_JE7_BE6.png",
        Dimension.NETHER: "icons/Netherrack_JE2_BE1.png",
        Dimension.END: "icons/End_Stone_JE3_BE2.png",
    }

    dimension_changed = pyqtSignal(object)

    @property
    def dimension(self) -> Dimension:
        """
        :return: The currently selected dimension.
        """

        return Dimension.fromMC(self.currentData())

    @dimension.setter
    def dimension(self, value: Dimension) -> None:
        self.setCurrentIndex(self.findData(value.getMCDim()))
    
    def __init__(self, parent: QWidget) -> None:
        super().__init__(parent)

        self.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContentsOnFirstShow)

        self.currentIndexChanged.connect(self._on_index_changed)

        for index, dimension in enumerate(Dimension.values()):
            self.addItem(str(dimension).capitalize() + "  ", dimension.getMCDim())
            if dimension in self._ICONS:
                self.setItemIcon(index, QIcon(Resources.INSTANCE.pixmap(self._ICONS[dimension])))

    def _on_index_changed(self, index: int) -> None:
        self.dimension_changed.emit(self.dimension)

    def set_dimension(self, dimension: Dimension) -> None:
        """
        Sets the current dimension that this combo box is showing.

        :param dimension: The dimension to set it to.
        """

        self.dimension = dimension
