#!/bin/sh
set -eu
: "${PG_USER:?Falta PG_USER}"
: "${PG_PASS:?Falta PG_PASS}"
: "${PG_DB:?Falta PG_DB}"
: "${PG_HOST:?Falta PG_HOST}"
: "${PG_PORT:?Falta PG_PORT}"
: "${CAFEFE_FRONTEND_ORIGIN:?Falta el origen exacto del navegador}"
case "$PG_PORT" in *[!0-9]*|'') echo 'PG_PORT debe ser numérico' >&2; exit 1;; esac
case "$CAFEFE_FRONTEND_ORIGIN" in
    http://*|https://*) ;;
    *) echo 'CAFEFE_FRONTEND_ORIGIN debe ser http(s)://host[:puerto]' >&2; exit 1;;
esac
origen_host=${CAFEFE_FRONTEND_ORIGIN#*://}
case "$origen_host" in ''|*[/?\#\ @\*]*) echo 'Origen inválido: sin ruta, comodines ni credenciales' >&2; exit 1;; esac
if [ ! -r /opt/war/cafefe.war ] || [ ! -s /opt/war/cafefe.war ]; then
    echo 'Monte un archivo WAR legible en /opt/war/cafefe.war (solo lectura).' >&2
    exit 1
fi
# Liberty requiere cifrar sus claves LTPA incluso con sesión propia de la aplicación.
# Se genera al ejecutar; nunca se incrusta un secreto en la imagen.
umask 077
archivo_clave=/opt/liberty/wlp/usr/servers/cafefe/ltpa-password
if [ ! -s "$archivo_clave" ]; then
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n' > "$archivo_clave"
fi
ltpa_keys_password=$(cat "$archivo_clave")
export ltpa_keys_password
exec /opt/liberty/wlp/bin/server run cafefe
