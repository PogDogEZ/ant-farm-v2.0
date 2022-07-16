#!/usr/bin/env python3

import re
from typing import Any, Dict, List, Union

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ..widgets.dimension import DimensionComboBox
from ..widgets.players import PlayerSelectionButton

from java.lang import Boolean, Double, Integer

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api.data import BlockChunkPosition, ChunkPosition, Dimension
from ez.pogdog.yescom.core.scanning import ITask
from ez.pogdog.yescom.ui.util import ReflectionUtil


class NewTaskDialog(QDialog):
    """
    Allows the user to create a new task by providing parameters.
    """

    INSTANCE: Union["NewTaskDialog", None] = None

    def __init__(
        self, 
        parent: QWidget, 
        dimension: Dimension = Dimension.OVERWORLD, 
        defaults: Union[Dict[str, Any], None] = None,
    ) -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE

        self.setWindowTitle("New task")
        # self.setWindowFlags(self.windowFlags() & Qt.WindowType.Tool)
        # self.setModal(True)
        # self.setWindowModality(Qt.WindowModality.WindowModal)

        self._setup(dimension, defaults)

        self.accepted.connect(self._on_accepted)
        self.finished.connect(self._on_finished)

    def show(self) -> None:
        # Don't show this window if it's not the currently open one
        if self.__class__.INSTANCE is not None and self != self.__class__.INSTANCE:
            self.__class__.INSTANCE.setWindowState(
                self.__class__.INSTANCE.windowState() & ~Qt.WindowState.WindowMinimized | Qt.WindowState.WindowActive,
                )
            self.__class__.INSTANCE.activateWindow()
            return
        self.__class__.INSTANCE = self
        super().show()

    def _setup(self, dimension: Dimension, defaults: Union[Dict[str, Any], None]) -> None:
        main_layout = QVBoxLayout(self)

        info_label = QLabel(self)
        info_label.setText("Choose a task to start and enter the required parameters:")
        main_layout.addWidget(info_label)

        task_layout = QGridLayout()

        task_label = QLabel(self)
        task_label.setText("Task:")
        task_layout.addWidget(task_label, 0, 0, 1, 1)

        self.task_combo_box = QComboBox(self)
        self.task_combo_box.setFixedWidth(self.fontMetrics().boundingRect("M" * 10).width())
        self.task_combo_box.currentIndexChanged.connect(self._on_task_index_changed)
        task_layout.addWidget(self.task_combo_box, 0, 1, 1, 1)

        dimension_label = QLabel(self)
        dimension_label.setText("Dimension:")
        dimension_label.setToolTip("The dimension to run the scan in.")
        task_layout.addWidget(dimension_label, 1, 0, 1, 1)

        self.dimension_combo_box = DimensionComboBox(self)
        self.dimension_combo_box.set_dimension(dimension)
        task_layout.addWidget(self.dimension_combo_box, 1, 1, 1, 1)

        target_label = QLabel(self)
        target_label.setText("Target:")
        target_label.setToolTip("Makes sure that the scan only runs when the given player is online.")
        task_layout.addWidget(target_label, 2, 0, 1, 1)

        self.target_selection_button = PlayerSelectionButton(self)
        task_layout.addWidget(self.target_selection_button, 2, 1, 1, 1)

        task_layout.addItem(QSpacerItem(20, 0, QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum), 1, 2, 1, 1)
        task_layout.setSpacing(2)
        task_layout.setContentsMargins(6, 0, 6, 0)  # Indent a little, this looks nicer IMO
        main_layout.addLayout(task_layout)

        self.parameters_label = QLabel(self)
        self.parameters_label.setText("Parameters (0):")
        main_layout.addWidget(self.parameters_label)

        self.parameters_table = NewTaskDialog.ParametersTable(self, defaults)
        main_layout.addWidget(self.parameters_table)

        for task in self.yescom.taskHandler.getTasks():
            self.task_combo_box.addItem(task.getName(), task)

        dialog_buttons = QDialogButtonBox(self)
        dialog_buttons.setStandardButtons(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        dialog_buttons.accepted.connect(self.accept)
        dialog_buttons.rejected.connect(self.reject)
        main_layout.addWidget(dialog_buttons)

    # ------------------------------ Events ------------------------------ #

    def _on_accepted(self) -> None:
        current = self.main_window.current_server
        if current is None:
            QMessageBox.warning(
                self.main_window, 
                "Can't start task",
                "Can't start task as no current server is selected.",
            )
            return

        task = self.task_combo_box.currentData()
        if task is None:
            QMessageBox.warning(self.main_window, "Can't start task", "Can't start task as no task is selected.")

        try:
            if current.addTask(task, self.dimension_combo_box.dimension, self.parameters_table.values):
                task.setTarget(self.target_selection_button.info)
                QMessageBox.information(self.main_window, "Started task", "The task was successfully started.")
            else:
                QMessageBox.warning(
                    self.main_window,
                    "Can't start task",
                    "Couldn't start task, please check the parameter values and try again.",
                )
                self.__class__.INSTANCE = None
                dialog = NewTaskDialog(self.parent(), self.dimension_combo_box.dimension, self.parameters_table.values)
                dialog.task_combo_box.setCurrentIndex(self.task_combo_box.currentIndex())
                dialog.target_selection_button.info = self.target_selection_button.info
                dialog.show()

        except Exception as error:
            QMessageBox.critical(
                self.main_window,
                "An error occurred",
                "An error occurred while starting the task: %r" % error,
            )

    def _on_finished(self, result: int) -> None:
        if self == self.__class__.INSTANCE:
            self.__class__.INSTANCE = None

    def _on_task_index_changed(self, index: int) -> None:
        task = self.task_combo_box.currentData()
        if task is None:  # ?
            parameters = []
        else:
            parameters = task.getParameters()

        self.parameters_label.setText("Parameters (%i):" % len(parameters))
        self.parameters_table.update(parameters)

    # ------------------------------ Events ------------------------------ #

    class ParametersTable(QTableWidget):
        """
        Lists the parameters that the task takes in.
        """

        def __init__(self, parent: "NewTaskDialog", defaults: Union[Dict[str, Any], None] = None) -> None:
            super().__init__(parent)

            self.main_window = MainWindow.INSTANCE

            self.values: Dict[str, Any] = {}
            self._defaults: Dict[str, Any] = {}

            if defaults is not None:
                self._defaults.update(defaults)

            self.setColumnCount(2)

            header = self.horizontalHeader()
            header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
            header.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)

        def _parse_chunk_position(self, text: str) -> Union[BlockChunkPosition, None]:
            match = re.match("(?P<x>(-?)[0-9]+),( *)(?P<z>(-?)[0-9]+)", text)
            if match is None:
                return None

            return BlockChunkPosition(int(match.group("x")), int(match.group("z")))

        def update(self, parameters: List[ITask.Parameter]) -> None:
            """
            Updates the parameters list.

            :param parameters: The of list parameters.
            """

            current = self.main_window.current_server

            self.values.clear()

            self.clear()
            self.setHorizontalHeaderLabels(["Name", "Value"])

            self.setRowCount(len(parameters))
            # self.parameters_label.setText("Parameters (%i):" % len(parameters))
            for index, parameter in enumerate(parameters):
                name_item = QTableWidgetItem()
                name_item.setText(parameter.name)
                name_item.setToolTip(parameter.description)
                name_item.setFlags(name_item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                self.setItem(index, 0, name_item)

                class_ = parameter.clazz
                default: Union[Any, None] = None
                if current is not None:
                    default = parameter.defaultValue.apply(current)
                if parameter.name in self._defaults:
                    default = self._defaults[parameter.name]

                if ReflectionUtil.isArray(class_):  # TODO: Arrays, somehow (without another dialog preferably)
                    ...

                elif ReflectionUtil.isEnum(class_):
                    option_combo_box = QComboBox(self)
                    # option_combo_box.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContents)
                    # option_combo_box.setFixedWidth(self.fontMetrics().boundingRect("M" * 12).width())
                    option_combo_box.currentIndexChanged.connect(
                        lambda index, parameter=parameter: self.values.update({
                            parameter.name: parameter.clazz.values()[option_combo_box.currentData()],
                        }),
                    )
                    self.setCellWidget(index, 1, option_combo_box)

                    for value in class_.values():
                        option_combo_box.addItem(str(value).capitalize(), value.ordinal())

                    if default is not None:
                        option_combo_box.setCurrentIndex(option_combo_box.findData(default.ordinal()))

                elif class_ == Boolean:
                    # main_layout.removeWidget(name_label)
                    option_checkbox = QCheckBox(self)
                    # option_checkbox.setText(self.parameter.name())
                    # option_checkbox.setToolTip(self.parameter.description())
                    option_checkbox.stateChanged.connect(
                        lambda state, parameter=parameter: self.values.update({
                            parameter.name: Boolean(state == Qt.CheckState.Checked),
                        }),
                    )
                    self.setCellWidget(index, 1, option_checkbox)

                    if default is not None:
                        option_checkbox.setChecked(default)

                elif class_ == Double:
                    option_spinbox = QDoubleSpinBox(self)
                    option_spinbox.setMinimum(-2147483647.0)  # TODO: Limits?
                    option_spinbox.setMaximum(2147483647.0)
                    option_spinbox.setSingleStep(0.1)
                    option_spinbox.valueChanged.connect(
                        lambda value, parameter=parameter: self.values.update({parameter.name: Double(value)}),
                    )
                    self.setCellWidget(index, 1, option_spinbox)

                    if default is not None:
                        option_spinbox.stepBy(int(default / option_spinbox.singleStep()))
                        option_spinbox.lineEdit().deselect()
                    # self.values[parameter.name] = Double(option_spinbox.value())

                elif class_ == Integer:
                    option_spinbox = QSpinBox(self)
                    option_spinbox.setMinimum(-2147483647)  # FIXME: ?
                    option_spinbox.setMaximum(2147483647)
                    # option_spinbox.setPrefix("")
                    option_spinbox.valueChanged.connect(
                        lambda value, parameter=parameter: self.values.update({parameter.name: Integer(value)}),
                    )
                    self.setCellWidget(index, 1, option_spinbox)

                    if default is not None:
                        option_spinbox.stepBy(int(default / option_spinbox.singleStep()))
                        option_spinbox.lineEdit().deselect()
                    # self.values[parameter.name] = Integer(option_spinbox.value())

                elif class_ in (BlockChunkPosition, ChunkPosition):
                    coordinates_edit = QLineEdit(self)
                    coordinates_edit.setPlaceholderText("X, Z...")
                    coordinates_edit.setValidator(QRegularExpressionValidator(
                        QRegularExpression("(-?)[0-9]+,( *)(-?)[0-9]+"), self),
                    )
                    coordinates_edit.textChanged.connect(
                        lambda text, parameter=parameter: self.values.update({parameter.name: self._parse_chunk_position(text)}),
                    )

                    if default is not None:
                        coordinates_edit.setText("%i, %i" % (default.getX() * 16, default.getZ() * 16))  # Will return a chunk position
                        # self.values[parameter.name] = default

                    self.setCellWidget(index, 1, coordinates_edit)

                else:  # TODO: Everything else
                    ...

            self._defaults.clear()  # Don't use the defaults the second time around


from ..main import MainWindow
