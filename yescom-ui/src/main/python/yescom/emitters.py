#!/usr/bin/env python3

from typing import List

from ez.pogdog.yescom.api import Logging
from ez.pogdog.yescom.api.event import Emitter
from ez.pogdog.yescom.core import Emitters
from ez.pogdog.yescom.ui.event import PyEmitter

logger = Logging.getLogger("yescom.emitters")

logger.fine("Localising all emitters...")

EMITTERS: List[Emitter] = []

for name in dir(Emitters):
    try:
        field = getattr(Emitters, name)
        if field.java_name == Emitter.java_name:
            logger.finest("Found emitter %s." % name)
            locals()[name] = PyEmitter(field)
            EMITTERS.append(locals()[name])

    except AttributeError:  # Actually faster than hasattr() :p
        ...

logger.fine("Localised %i emitters." % len(EMITTERS))
