"""Ajuste del bucle de eventos de asyncio en Windows.

**El problema.** Windows usa por defecto ``ProactorEventLoop``, que se apoya en
IOCP. ``psycopg`` en modo asíncrono no puede funcionar sobre él y falla nada más
conectar con::

    psycopg.InterfaceError: Psycopg cannot use the 'ProactorEventLoop' to run in
    async mode.

**La solución.** Cambiar a ``SelectorEventLoop``, que sí es compatible. Hay que
hacerlo **antes** de crear cualquier bucle, así que se llama al principio de
todo: en ``app/main.py`` y en ``migrations/env.py``.

En Linux —es decir, dentro de Docker, que es donde el servicio corre de
verdad— esta función no hace nada: el bucle por defecto ya es compatible. El
ajuste solo hace falta para desarrollar y depurar en Windows.
"""

from __future__ import annotations

import asyncio
import logging
import sys

log = logging.getLogger(__name__)


def ajustar_bucle_para_windows() -> None:
    """Si estamos en Windows, cambia al SelectorEventLoop.

    Es idempotente: llamarla dos veces no rompe nada.
    """
    if sys.platform != "win32":
        return

    politica_actual = asyncio.get_event_loop_policy()
    if isinstance(politica_actual, asyncio.WindowsSelectorEventLoopPolicy):
        return

    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    log.debug(
        "Windows detectado: se cambió a SelectorEventLoop porque psycopg no puede "
        "trabajar de forma asíncrona sobre ProactorEventLoop."
    )
