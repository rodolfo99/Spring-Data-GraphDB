#!/usr/bin/env python3
"""Prueba HTTP contra el backend. Solo elimina el libro creado por esta ejecución."""

import json
import sys
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

server = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
base_url = server.rstrip("/") + "/api/libros"


def call(method, path="", body=None):
    """Envía JSON y admite respuestas sin cuerpo, como DELETE 204."""
    request = Request(
        base_url + path,
        data=None if body is None else json.dumps(body).encode(),
        method=method,
        headers={"Content-Type": "application/json"},
    )

    with urlopen(request, timeout=30) as response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None


book_id = None

try:
    marker = "Prueba-" + uuid.uuid4().hex
    status, book = call(
        "POST",
        body={"titulo": marker, "autor": "Rodolfo", "anio": 2026},
    )
    assert status == 201
    book_id = book["id"]

    _, found = call("GET", "?titulo=" + marker)
    assert found["totalElementos"] == 1
    assert found["contenido"][0]["id"] == book_id

    # El ID debe conservarse aunque se actualice la entidad más de una vez.
    for title in [marker + "-v2", marker + "-v3"]:
        _, updated = call(
            "PUT",
            "/" + book_id,
            {"titulo": title, "autor": "Rodolfo", "anio": 2027},
        )
        assert updated["id"] == book_id

        _, saved = call("GET", "/" + book_id)
        assert saved["titulo"] == title

    status, _ = call("DELETE", "/" + book_id)
    assert status == 204

    try:
        call("GET", "/" + book_id)
        raise AssertionError("El libro sigue existiendo")
    except HTTPError as error:
        assert error.code == 404

    # Repetir un borrado debe ser una operación idempotente.
    status, _ = call("DELETE", "/" + book_id)
    assert status == 204
    book_id = None

    print("OK: crear, buscar, actualizar dos veces, consultar y borrar.")
finally:
    if book_id:
        call("DELETE", "/" + book_id)
