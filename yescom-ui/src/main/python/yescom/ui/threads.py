#!/usr/bin/env python3

import requests
import time
from typing import Dict, Tuple

from PyQt6.QtCore import *
from PyQt6.QtGui import *

from .. import emitters

from java.util import UUID

from ez.pogdog.yescom import YesCom
from ez.pogdog.yescom.api import Logging

logger = Logging.getLogger("yescom.ui.threads")


class EventQueueThread(QThread):
    """
    Due to limitations, calls to interpreters can only be made through the same Java thread that created the
    interpreter, so events need to be accessed in a queued fashion.
    """

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()
        self.main_window = MainWindow.INSTANCE

    def run(self) -> None:
        while self.yescom.isRunning():
            start = time.time()
            for emitter in emitters.EMITTERS:
                if not emitter.queuedEvents.isEmpty():
                    while not emitter.queuedEvents.isEmpty():
                        with emitter.synchronized():
                            object_ = emitter.queuedEvents.remove()
                        # This shouldn't require a lock, right? We barely add listeners throughout
                        for listener in emitter.pyListeners:
                            listener(object_)
            elapsed = (time.time() - start) * 1000
            if elapsed < 50:
                QThread.msleep(50 - int(elapsed))  # Damn

            # Fake tick event, I know, but in fairness the real tick event would also fire here, so we might as well
            # just skip the middle man
            MainWindow.INSTANCE.tick.emit()


class SkinDownloaderThread(QThread):
    """
    Downloads skins in the background, for the icon view.
    """

    skin_resolved = pyqtSignal(tuple)

    def __init__(self, parent: "MainWindow") -> None:
        super().__init__(parent)

        self.yescom = YesCom.getInstance()

        self._cache_mutex = QMutex()

        self._requests: Dict[UUID, int] = {}
        self._cache: Dict[UUID, Tuple[int, QPixmap]] = {}

    def run(self) -> None:
        player_cache = self.yescom.playersHandler.getPlayerCache()
        while self.yescom.isRunning():
            cant_download = 0

            while len(self._requests) > cant_download:
                uuid, download_time = next(iter(self._requests.items()))  # Should maintain insertion order

                if download_time > time.time():  # Can't download this one right now
                    cant_download += 1

                elif not uuid in player_cache:
                    cant_download += 1
                    self._requests[uuid] += 10  # Try again in 10 seconds
                    logger.fine("Can't download skin for %s as they have not been cached yet." % uuid)

                else:
                    player_info = player_cache[uuid]

                    if player_info.skinURL:
                        try:
                            logger.finest("Looking up skin for %s (%r)..." % (uuid, player_info.skinURL))
                            request = requests.get(player_info.skinURL)
                            if request.status_code == 200:
                                player_skin = QPixmap()
                                player_skin.loadFromData(
                                    request.content, format="PNG",
                                    flags=Qt.ImageConversionFlag.AutoColor | Qt.ImageConversionFlag.AutoDither,
                                )
                                player_head = player_skin.copy(8, 8, 8, 8)
                                player_head = player_head.scaled(32, 32, transformMode=Qt.TransformationMode.FastTransformation)

                                # Cache expires in 30 minutes
                                self._cache_mutex.lock()
                                self._cache[uuid] = (int(time.time()) + 1800, player_head)
                                self._cache_mutex.unlock()

                                self.skin_resolved.emit((uuid, player_head))
                                del player_skin
                                del self._requests[uuid]

                            else:
                                raise Exception("Status code %i." % request.status_code)

                        except Exception as error:
                            cant_download += 1
                            self._requests[uuid] += 20  # Try again in 20 seconds
                            logger.fine("Couldn't download skin for %s: %r." % (uuid, error))

                    else:
                        cant_download += 1
                        self._requests[uuid] += 10
                        logger.fine("Can't download skin for %s as their skin URL has not been cached yet." % uuid)

            QThread.msleep(250)

            self._cache_mutex.lock()

            current_time = time.time()
            expired = []

            for uuid, (expiry, _) in self._cache.items():
                if expiry < current_time:
                    expired.append(uuid)
            for uuid in expired:
                del self._cache[uuid]

            removed = bool(expired)
            expired.clear()

            removed |= len(self._cache) > 128
            while len(self._cache) > 128:  # Forcefully don't let it get too big
                self._cache.popitem()

            if removed:
                logger.finest("Skin cache size is %i." % len(self._cache))

            self._cache_mutex.unlock()

    def request_skin(self, uuid: UUID) -> None:
        """
        Requests that a skin be downloaded for the given UUID.

        :param uuid: The UUID to download the skin for.
        """

        self._cache_mutex.lock()
        if uuid in self._cache:
            expiry, player_head = self._cache[uuid]
            self._cache[uuid] = (expiry + 1800, player_head)
            self._cache_mutex.unlock()
            self.skin_resolved.emit((uuid, player_head))
            return
        self._cache_mutex.unlock()

        # Download immediately, it's fine if we reset a delayed download, this can happen if we have requested the skin
        # for the accounts tab and then the overview tab. This is actually better as by the time we're requesting skins
        # for the overview tab, we'll have logged in and therefore will have the URL, so downloading immediately will
        # make the request appear faster to the user
        self._requests[uuid] = int(time.time()) - 1


from .main import MainWindow
