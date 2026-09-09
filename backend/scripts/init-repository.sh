#!/bin/sh
set -eu

server="${GRAPHDB_SERVER:-http://localhost:7200}"
config="${GRAPHDB_CONFIG:-/config/repository.ttl}"
i=0

# Esperar al servidor antes de consultar o crear el repositorio.
until curl -fsS --max-time 5 "$server/protocol" >/dev/null 2>&1; do
    i=$((i + 1))

    if [ "$i" -ge 120 ]; then
        echo "GraphDB no esta disponible tras esperar el arranque" >&2
        exit 1
    fi

    sleep 2
done

status=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "$server/rest/repositories/libros")

case "$status" in
    200)
        echo "El repositorio libros ya existe; se conserva su configuracion y contenido."
        ;;
    404)
        curl -fsS --max-time 60 -X POST "$server/rest/repositories" -F "config=@$config"
        echo "Repositorio libros creado."
        ;;
    *)
        echo "No se pudo comprobar el repositorio: HTTP $status" >&2
        exit 1
        ;;
esac
