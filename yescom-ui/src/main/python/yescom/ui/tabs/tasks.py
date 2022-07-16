#!/usr/bin/env python3

import datetime
import time
from typing import Dict, Tuple, Union

from PyQt6.QtCore import *
from PyQt6.QtGui import *
from PyQt6.QtWidgets import *

from ..dialogs.tasks import NewTaskDialog
from ..widgets.options import OptionsItem

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.data import BlockChunkPosition, ChunkPosition, Dimension
from ez.pogdog.yescom.api.data.player import PlayerInfo
from ez.pogdog.yescom.core.config import Option
from ez.pogdog.yescom.core.connection import Server
from ez.pogdog.yescom.core.scanning import IScanner, ITask
from ez.pogdog.yescom.ui.util import ReflectionUtil

logger = Logging.getLogger("yescom.ui.tabs.tasks")


class TasksTab(QWidget):
    """
    Allows the user to manage tasks running on YesCom.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.main_window = MainWindow.INSTANCE

        self._setup_tab()

        self.main_window.tick.connect(self._on_tick)

    def __repr__(self) -> str:
        return "<TasksTab() at %x>" % id(self)

    def _setup_tab(self) -> None:
        logger.finer("Setting up tasks tab...")

        main_layout = QVBoxLayout(self)

        trees_layout = QHBoxLayout()

        self.scanners_tree = TasksTab.ScannersTree(self)
        trees_layout.addWidget(self.scanners_tree)
        self.tasks_tree = TasksTab.TasksTree(self)
        trees_layout.addWidget(self.tasks_tree)

        main_layout.addLayout(trees_layout)
        buttons_layout = QHBoxLayout()

        self.new_task_button = QPushButton(self)
        self.new_task_button.setText("New task")
        self.new_task_button.setToolTip("Starts a new task.")
        self.new_task_button.clicked.connect(self._on_new_task)
        buttons_layout.addWidget(self.new_task_button)

        self.pause_all_button = QPushButton(self)
        self.pause_all_button.setText("Pause all")
        self.pause_all_button.setToolTip("Pauses all current tasks and scanners.")
        self.pause_all_button.clicked.connect(self._on_pause_all)
        buttons_layout.addWidget(self.pause_all_button)

        self.resume_all_button = QPushButton(self)
        self.resume_all_button.setText("Resume all")
        self.resume_all_button.setToolTip("Resumes all current tasks and scanners.")
        self.resume_all_button.clicked.connect(self._on_resume_all)
        buttons_layout.addWidget(self.resume_all_button)

        main_layout.addLayout(buttons_layout)

    # ------------------------------ Events ------------------------------ #

    def _on_tick(self) -> None:
        current = self.main_window.current_server
        if current is None:
            self.pause_all_button.setEnabled(False)
            self.resume_all_button.setEnabled(False)
            return

        all_paused = True
        all_unpaused = True
        for scanner in current.getScanners():
            if scanner.isPaused():
                all_paused = False
            else:
                all_unpaused = False
        for task in current.getTasks():
            if task.isPaused():
                all_paused = False
            else:
                all_unpaused = False

        self.pause_all_button.setEnabled(not all_unpaused)
        self.resume_all_button.setEnabled(not all_paused)

    def _on_new_task(self, checked: bool) -> None:
        new_task_dialog = NewTaskDialog(self)
        new_task_dialog.show()

    def _on_pause_all(self, checked: bool) -> None:
        current = self.main_window.current_server
        if current is not None:
            for scanner in current.getScanners():
                scanner.pause()
            for task in current.getTasks():
                task.pause()

    def _on_resume_all(self, checked: bool) -> None:
        current = self.main_window.current_server
        if current is not None:
            for scanner in current.getScanners():
                scanner.unpause()
            for task in current.getTasks():
                task.unpause()

    # ------------------------------ Classes ------------------------------ #

    class ScannersTree(QTreeWidget):
        """
        Displays a list of all scanners running on the current server.
        """

        def __init__(self, parent: "TasksTab") -> None:
            super().__init__(parent)

            self.main_window = MainWindow.INSTANCE

            self.setColumnCount(2)
            self.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
            self.setHeaderLabels(["Scanners (0):", ""])

            header = self.header()
            header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
            header.setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)

            self.main_window.server_changed.connect(lambda: QTimer.singleShot(50, self._update_count))

            self.main_window.scanner_added.connect(self._on_scanner_added)

            if self.main_window.current_server is not None:
                for scanner in self.main_window.current_server.getScanners():
                    self._on_scanner_added(scanner, skip_check=True)

        # ------------------------------ Events ------------------------------ #

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            current = self.currentItem()
            if current is None:
                return

            menu = QMenu()

            while current is not None:
                if isinstance(current, TasksTab.ScannerItem):
                    break
                current = current.parent()
            else:
                logger.warning("Don't know how to handle item %s." % current)
                return

            menu.addAction("Manage scanner...")  # TODO: Managing scanners

            menu.addSeparator()

            pause_resume = menu.addAction("Pause", lambda: self._pause(current.scanner))
            if current.scanner.isPaused():
                pause_resume.setText("Resume")
                pause_resume.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPlay))
            else:
                pause_resume.setText("Pause")
                pause_resume.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPause))
            restart = menu.addAction("Restart", current.scanner.restart)
            restart.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_BrowserReload))

            menu.addSeparator()

            copy_coords = menu.addMenu("Copy coords")
            for dimension in current.scanner.getApplicableDimensions():
                copy_coords.addAction(
                    str(dimension).capitalize(), lambda dimension=dimension: self._copy_coords(current.scanner, dimension),
                )

            menu.exec(event.globalPos())

        def _on_scanner_added(self, scanner: IScanner, skip_check: bool = False) -> None:
            if not skip_check:
                for index in range(self.topLevelItemCount()):
                    top_level_item = self.topLevelItem(index)
                    if isinstance(top_level_item, TasksTab.ScannerItem) and top_level_item.scanner == scanner:
                        return
            self.addTopLevelItem(TasksTab.ScannerItem(self, scanner))
            self._update_count()

        # ------------------------------ Utility ------------------------------ #

        def _pause(self, scanner: IScanner) -> None:
            if scanner.isPaused():
                scanner.unpause()
            else:
                scanner.pause()

        def _copy_coords(self, scanner: IScanner, dimension: Dimension) -> None:
            position = scanner.getCurrentPosition(dimension)
            QApplication.clipboard().setText("%i, %i" % (position.getX() * 16, position.getZ() * 16))

        def _update_count(self) -> None:
            """
            Updates the current item count.
            """

            count = 0
            for index in range(self.topLevelItemCount()):
                top_level_item = self.topLevelItem(index)
                if not top_level_item.isHidden():  # Children of this class may have hidden items for performance
                    count += 1

            self.setHeaderLabels(["Scanners (%i):" % count, ""])

    class ScannerItem(QTreeWidgetItem):
        """
        Represents a scanner running on the current server.
        """

        def __init__(self, parent: "TasksTab.ScannersTree", scanner: IScanner) -> None:
            super().__init__(parent)

            self.yescom = YesCom.getInstance()
            self.main_window = MainWindow.INSTANCE

            self.scanner = scanner

            self._setup()

            self._on_tick()
            self._on_server_changed()

            self.main_window.tick.connect(self._on_tick)
            self.main_window.server_changed.connect(self._on_server_changed)

        def _setup(self) -> None:
            self.setText(0, self.scanner.getName())
            self.setToolTip(0, self.scanner.getDescription())

            # description_child = QTreeWidgetItem(self, ["Description:", scanner_info.description()])
            # description_child.setToolTip(1, scanner_info.description())
            # self.addChild(description_child)
            self.addChild(QTreeWidgetItem(self, [
                "Dimensions:", ", ".join(map(lambda value: str(value).lower(), self.scanner.getApplicableDimensions())),
            ]))
            # self.paused_child = QTreeWidgetItem(self, ["Paused:", str(self.scanner.isPaused())])
            # self.addChild(self.paused_child)

            self.current_position_child = QTreeWidgetItem(self, ["Current position"])
            self.current_position_child.setExpanded(True)
            self.current_position_children: Dict[Dimension, QTreeWidgetItem] = {}

            for dimension in self.scanner.getApplicableDimensions():
                dimension_child = QTreeWidgetItem(self.current_position_child, [str(dimension).capitalize() + ":", ""])
                self.current_position_child.addChild(dimension_child)
                self.current_position_children[dimension] = dimension_child

            self.addChild(OptionsItem(self, self.scanner))

        # ------------------------------ Events ------------------------------ #

        def _on_tick(self) -> None:
            if self.scanner.isPaused():
                self.setIcon(0, self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPause))
            else:
                self.setIcon(0, self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPlay))
                # self.setIcon(0, QIcon())
            # self.paused_child.setText(1, str(self.scanner.isPaused()))

            if self.isExpanded() and self.current_position_child.isExpanded():
                for dimension, child in self.current_position_children.items():
                    current_position = self.scanner.getCurrentPosition(dimension)
                    text = "%i, %i" % (current_position.getX() * 16, current_position.getZ() * 16)
                    child.setText(1, text)
                    child.setToolTip(1, text)

        def _on_server_changed(self) -> None:
            self.setHidden(self.main_window.current_server != self.scanner.getServer())

    class TasksTree(QTreeWidget):
        """
        Displays a list of all tasks running on the current server.
        """

        def __init__(self, parent: "TasksTab") -> None:
            super().__init__(parent)

            self.main_window = MainWindow.INSTANCE

            self.setColumnCount(2)
            self.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
            self.setHeaderLabels(["Tasks (0):", ""])

            # TODO: Would be nice to be able to filter tasks based on dimension, type, results, etc...

            header = self.header()
            header.setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
            header.setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)

            self.main_window.server_changed.connect(lambda: QTimer.singleShot(50, self._update_count))

            self.main_window.task_added.connect(self._on_task_added)
            self.main_window.task_removed.connect(self._on_task_removed)

        # ------------------------------ Events ------------------------------ #

        def contextMenuEvent(self, event: QContextMenuEvent) -> None:
            current = self.currentItem()
            if current is None:
                return

            menu = QMenu()

            while current is not None:
                if isinstance(current, TasksTab.TaskItem):
                    break
                current = current.parent()
            else:
                logger.warning("Don't know how to handle item %s." % current)
                return

            menu.addAction("Manage task...")  # TODO: Managing tasks

            menu.addSeparator()

            pause_resume = menu.addAction("Pause", lambda: self._pause(current.task))
            if current.task.isPaused():
                pause_resume.setText("Resume")
                pause_resume.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPlay))
            else:
                pause_resume.setText("Pause")
                pause_resume.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPause))
            restart = menu.addAction("Restart", current.task.restart)
            restart.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_BrowserReload))
            stop = menu.addAction("Stop", current.task.cancel)
            stop.setIcon(self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaStop))

            menu.addSeparator()

            copy_coords = menu.addAction("Copy coords")

            menu.exec(event.globalPos())

        def _on_task_added(self, task: ITask) -> None:
            self.addTopLevelItem(TasksTab.TaskItem(self, task))
            self._update_count()

        def _on_task_removed(self, task: ITask) -> None:
            for index in range(self.topLevelItemCount()):
                top_level_item = self.topLevelItem(index)
                if isinstance(top_level_item, TasksTab.TaskItem) and top_level_item.task == task:
                    self.takeTopLevelItem(index)
                    break
            self._update_count()

        # ------------------------------ Utility ------------------------------ #

        def _pause(self, task: ITask) -> None:
            if task.isPaused():
                task.unpause()
            else:
                task.pause()

        # def _copy_coords(self, task: ITask) -> None:
        #     position = task.getCurrentPosition()
        #     QApplication.clipboard().setText("%i, %i" % (position.getX() * 16, position.getZ() * 16))

        def _update_count(self) -> None:
            count = 0
            for index in range(self.topLevelItemCount()):
                top_level_item = self.topLevelItem(index)
                if not top_level_item.isHidden():  # Children of this class may have hidden items for performance
                    count += 1

            self.setHeaderLabels(["Tasks (%i):" % count, ""])

    class TaskItem(QTreeWidgetItem):
        """
        Represents a task that was started by the user.
        """

        def __init__(self, parent: "TasksTree", task: ITask) -> None:
            super().__init__(parent)

            self.main_window = MainWindow.INSTANCE

            self.task = task
            self._target: Union[PlayerInfo, None] = None

            self._setup()

            self.main_window.skin_downloader_thread.skin_resolved.connect(self._on_skin_resolved)

            self._on_tick()
            self._on_server_changed()

            self.main_window.tick.connect(self._on_tick)
            self.main_window.server_changed.connect(self._on_server_changed)

        def _setup(self) -> None:
            self.setText(0, "%s (%.1f%%)" % (self.task.getName(), self.task.getProgress() * 100))
            self.setToolTip(0, self.task.getDescription())

            started_child = QTreeWidgetItem(self, [
                "Started:", str(datetime.datetime.fromtimestamp(self.task.getStartTime() // 1000)),
            ])
            self.addChild(started_child)

            dimension_child = QTreeWidgetItem(self, ["Dimension:", str(self.task.getDimension()).lower()])
            self.addChild(dimension_child)

            self.current_position_child = QTreeWidgetItem(self, ["Current position:", ""])
            self.addChild(self.current_position_child)

            self.elapsed_child = QTreeWidgetItem(self, ["Elapsed:", ""])
            self.elapsed_child.setToolTip(0, "The time that has elapsed since the task was started.")
            self.addChild(self.elapsed_child)

            self.target_child = QTreeWidgetItem(self, ["Target:", "None"])
            self.target_child.setToolTip(0, "The task will only scan when the \"target\" player is online.")
            self.addChild(self.target_child)

            parameter_values = self.task.getParameterValues()
            if parameter_values:
                parameters_child = QTreeWidgetItem(self, ["Parameters (%i)" % len(parameter_values)])
                parameters_child.setToolTip(0, "The parameters that the task was started with.")
                self.addChild(parameters_child)

                for parameter_value in parameter_values:
                    value_item = QTreeWidgetItem(parameters_child, [parameter_value.parameter.name + ":", ""])
                    value_item.setToolTip(0, parameter_value.parameter.description)

                    class_ = parameter_value.parameter.clazz
                    value = "unknown"

                    if ReflectionUtil.isArray(class_):
                        ...

                    elif ReflectionUtil.isEnum(class_):
                        value = str(parameter_value.value).lower()

                    elif class_ in (BlockChunkPosition, ChunkPosition):
                        value = "%i, %i" % (parameter_value.value.getX() * 16, parameter_value.value.getZ() * 16)

                    else:
                        value = str(parameter_value.value)

                    value_item.setText(1, value)
                    value_item.setToolTip(1, value)

                    parameters_child.addChild(value_item)

        # ------------------------------ Events ------------------------------ #

        def _on_skin_resolved(self, resolved: Tuple[UUID, QPixmap]) -> None:
            if self._target is not None and resolved[0] == self._target.uuid:
                self.target_child.setIcon(1, QIcon(resolved[1]))

        def _on_tick(self) -> None:
            self.setText(0, "%s (%.1f%%)" % (self.task.getName(), self.task.getProgress() * 100))
            if self.task.isPaused():
                self.setIcon(0, self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPause))
            else:
                self.setIcon(0, self.main_window.style().standardIcon(QStyle.StandardPixmap.SP_MediaPlay))

            if self.isExpanded():
                current_position = self.task.getCurrentPosition()
                current_position = "%i, %i" % (current_position.getX() * 16, current_position.getZ() * 16)
                self.current_position_child.setText(1, current_position)
                self.current_position_child.setToolTip(1, current_position)

                elapsed = str(datetime.timedelta(seconds=int(time.time()) - self.task.getStartTime() // 1000))
                self.elapsed_child.setText(1, elapsed)
                self.elapsed_child.setToolTip(1, elapsed)

                target = self.task.getTarget()
                if target != self._target:
                    self._target = target
                    if target is None:
                        self.target_child.setText(1, "None")
                        self.target_child.setIcon(1, QIcon())
                    else:
                        self.target_child.setText(1, target.username)
                        self.main_window.skin_downloader_thread.request_skin(target.uuid)

        def _on_server_changed(self) -> None:
            self.setHidden(self.main_window.current_server != self.task.getServer())


from ..main import MainWindow
