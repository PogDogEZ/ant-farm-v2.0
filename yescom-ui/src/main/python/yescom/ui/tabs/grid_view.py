#!/usr/bin/env python3

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ..renderer.grid import GridRenderer
from ..resources import Resources

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data import Dimension

logger = Logging.getLogger("yescom.ui.tabs.grid_view")


class GridViewTab(QWidget):
    """
    A tab that shows a grid view of loaded chunks and tracked players.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.main_window = parent

        self._setup_tab()

    def __repr__(self) -> str:
        return "<GridViewTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        logger.finer("Setting up grid view tab...")

        main_layout = QVBoxLayout(self)

        # main_splitter = QSplitter(self)
        # main_splitter.setOrientation(Qt.Vertical)

        self.renderer = GridRenderer(self)

        controls_layout = QGridLayout()

        dimension_label = QLabel(self)
        dimension_label.setText("Dimension:")
        controls_layout.addWidget(dimension_label, 0, 0, 1, 1)

        self.dimension_combo_box = QComboBox(self)
        self.dimension_combo_box.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContentsOnFirstShow)
        self.dimension_combo_box.currentIndexChanged.connect(self._on_dimension_changed)

        self.dimension_combo_box.addItem("Overworld  ", Dimension.OVERWORLD)
        self.dimension_combo_box.setItemIcon(0, QIcon(Resources.INSTANCE.pixmap("icons/Grass_Block_JE7_BE6.png")))
        self.dimension_combo_box.addItem("Nether  ", Dimension.NETHER)
        self.dimension_combo_box.setItemIcon(1, QIcon(Resources.INSTANCE.pixmap("icons/Netherrack_JE2_BE1.png")))
        self.dimension_combo_box.addItem("End  ", Dimension.END)
        self.dimension_combo_box.setItemIcon(2, QIcon(Resources.INSTANCE.pixmap("icons/End_Stone_JE3_BE2.png")))
        controls_layout.addWidget(self.dimension_combo_box, 0, 1, 1, 1)

        selection_label = QLabel(self)
        selection_label.setText("Selection mode:")
        controls_layout.addWidget(selection_label, 0, 2, 1, 1)

        self.selection_combo_box = QComboBox(self)
        self.selection_combo_box.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContentsOnFirstShow)
        self.selection_combo_box.currentIndexChanged.connect(self._on_selection_changed)

        self.selection_combo_box.addItem("None  ", GridRenderer.Selection.Mode.NONE)
        self.selection_combo_box.addItem("Line  ", GridRenderer.Selection.Mode.LINE)
        self.selection_combo_box.addItem("Box  ", GridRenderer.Selection.Mode.BOX)
        controls_layout.addWidget(self.selection_combo_box, 0, 3, 1, 1)

        controls_layout.addItem(QSpacerItem(40, 20, QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum), 0, 4, 1, 1)
        # controls_layout.addItem(QSpacerItem(40, 20, QSizePolicy.Expanding, QSizePolicy.Minimum), 1, 2, 1, 1)

        main_layout.addLayout(controls_layout)
        main_layout.addWidget(self.renderer)

        # self.trackers_tree = QTreeWidget(self)
        # self.trackers_tree.setHeaderLabel("Trackers (0):")
        # main_splitter.addWidget(self.trackers_tree)

        # main_layout.addWidget(main_splitter)

    def _on_dimension_changed(self, index: int) -> None:
        self.renderer.dimension = self.dimension_combo_box.currentData()

    def _on_selection_changed(self, index: int) -> None:
        self.renderer.selection_mode = self.selection_combo_box.currentData()


from ..main import MainWindow
