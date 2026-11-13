#!/usr/bin/env python3
"""Pruebas HTTP de Caja en entorno LOCAL de desarrollo.

Verifica consultas, campos vacios/tipos JSON incorrectos y CRUD positivo con
una caja temporal. La limpieza borra SOLO cajas con la etiqueta de esta ejecucion.
NO ejecutar contra produccion. No modifica facturas existentes.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/cajas'
ETIQUETA = 'PRUEBA_REST_CAJA_' + uuid.uuid4().hex[:16].upper()


def pedir(metodo, ruta='', cuerpo=None):
    datos = None
    if cuerpo is not None:
        datos = cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo, ensure_ascii=False).encode('utf-8')
    solicitud = urllib.request.Request(BASE + ruta, data=datos, method=metodo,
        headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(solicitud, timeout=12) as r:
            estado, texto = r.status, r.read().decode('utf-8', errors='replace')
    except urllib.error.HTTPError as ex:
        estado, texto = ex.code, ex.read().decode('utf-8', errors='replace')
    except (urllib.error.URLError, TimeoutError) as ex:
        return 0, {'conexion': str(ex)}
    try:
        return estado, json.loads(texto) if texto else None
    except ValueError:
        return estado, texto


def main():
    print('API:', BASE)
    print('Etiqueta de la caja temporal:', ETIQUETA)
    print('Solo usar en desarrollo; la caja creada se elimina al finalizar.')
    ok = 0
    fallos = 0
    id_creada = None

    def verificar(nombre, metodo, ruta, cuerpo, esperado, comprobacion=None):
        nonlocal ok, fallos
        estado, respuesta = pedir(metodo, ruta, cuerpo)
        bien = estado == esperado and (comprobacion is None or comprobacion(respuesta))
        if bien:
            ok += 1
            print(f'OK      {nombre:<47} HTTP {estado}')
        else:
            fallos += 1
            print(f'REVISAR {nombre:<47} HTTP {estado} (esperado {esperado}) {str(respuesta)[:230]}')
        return estado, respuesta

    def json_valido(**cambios):
        valor = {'nombre': ETIQUETA, 'activo': True, 'observaciones': 'Prueba REST temporal'}
        valor.update(cambios)
        return valor

    inexistente = str(uuid.uuid4())
    try:
        verificar('GET listar cajas', 'GET', '', None, 200, lambda v: isinstance(v, list))
        verificar('GET UUID inválido', 'GET', '/mal-id', None, 400)
        verificar('GET caja inexistente', 'GET', '/' + inexistente, None, 404)
        verificar('PUT UUID inválido', 'PUT', '/mal-id', json_valido(), 400)
        verificar('PUT caja inexistente', 'PUT', '/' + inexistente, json_valido(), 404)
        verificar('DELETE UUID inválido', 'DELETE', '/mal-id', None, 400)
        verificar('DELETE caja inexistente', 'DELETE', '/' + inexistente, None, 404)

        # Validaciones negativas, no escriben en base de datos.
        malas = [
            ('POST sin JSON', b'', 400),
            ('POST JSON malformado', b'{', 400),
            ('POST arreglo JSON', b'[]', 400),
            ('POST objeto vacío', {}, 422),
            ('POST sin nombre', {'activo': True}, 422),
            ('POST nombre null', json_valido(nombre=None), 422),
            ('POST nombre en blanco', json_valido(nombre=' \t '), 422),
            ('POST nombre un carácter', json_valido(nombre='a'), 422),
            ('POST nombre demasiado largo', json_valido(nombre='x' * 156), 422),
            ('POST nombre número', json_valido(nombre=123), 400),
            ('POST sin activo', {'nombre': 'Caja prueba'}, 422),
            ('POST activo null', json_valido(activo=None), 422),
            ('POST activo texto', json_valido(activo='true'), 400),
            ('POST activo número', json_valido(activo=1), 400),
            ('POST observaciones número', json_valido(observaciones=12), 400),
            ('POST observaciones demasiado largas', json_valido(observaciones='x' * 1001), 422),
            ('POST cliente intenta elegir UUID', dict(json_valido(), idCaja=inexistente), 400),
            ('POST intenta enviar facturas', dict(json_valido(), facturaCollection=[]), 400),
        ]
        for nombre, cuerpo, esperado in malas:
            verificar(nombre, 'POST', '', cuerpo, esperado)

        # Crear, consultar, modificar y borrar una caja identificable de prueba.
        estado, cuerpo = verificar('POST crear caja temporal', 'POST', '', json_valido(), 201,
                                  lambda v: isinstance(v, dict) and bool(v.get('idCaja'))
                                  and v.get('nombre') == ETIQUETA)
        if estado == 201 and isinstance(cuerpo, dict) and cuerpo.get('idCaja'):
            id_creada = cuerpo['idCaja']
            try:
                uuid.UUID(id_creada)
            except ValueError:
                print('REVISAR ID recibido no es UUID:', id_creada)
                fallos += 1
                id_creada = None

        if id_creada:
            ruta = '/' + id_creada
            verificar('GET caja recién creada', 'GET', ruta, None, 200,
                     lambda v: isinstance(v, dict) and v.get('nombre') == ETIQUETA)
            verificar('PUT nombre vacío no altera la caja', 'PUT', ruta,
                     json_valido(nombre='  '), 422)
            verificar('PUT activo tipo incorrecto', 'PUT', ruta,
                     json_valido(activo='false'), 400)
            verificar('GET aún conserva nombre original', 'GET', ruta, None, 200,
                     lambda v: isinstance(v, dict) and v.get('nombre') == ETIQUETA)
            verificar('PUT modificar caja', 'PUT', ruta,
                     json_valido(nombre=ETIQUETA + '_EDITADA', activo=False), 200,
                     lambda v: isinstance(v, dict) and v.get('nombre') == ETIQUETA + '_EDITADA'
                     and v.get('activo') is False)
            verificar('GET confirma estado inactivo', 'GET', ruta, None, 200,
                     lambda v: isinstance(v, dict) and v.get('activo') is False)
        else:
            print('OMITIR comprobaciones de edición: no hay ID válido de caja temporal')
    finally:
        # Limpieza restringida a la caja identificada; no tocar datos ajenos.
        candidatos = [id_creada] if id_creada else []
        if not candidatos:
            estado, cajas = pedir('GET')
            if estado == 200 and isinstance(cajas, list):
                candidatos = [c['idCaja'] for c in cajas if isinstance(c, dict)
                              and isinstance(c.get('nombre'), str)
                              and c['nombre'].startswith(ETIQUETA)
                              and isinstance(c.get('idCaja'), str)]
        for ident in candidatos:
            estado, salida = pedir('DELETE', '/' + ident)
            if estado == 200:
                ok += 1
                print('OK      Limpieza: caja temporal eliminada')
                estado2, _ = pedir('GET', '/' + ident)
                if estado2 == 404:
                    ok += 1
                    print('OK      Limpieza: GET devuelve 404')
                else:
                    fallos += 1
                    print('REVISAR limpieza: GET posterior HTTP', estado2)
            else:
                fallos += 1
                print('REVISAR limpieza manual de caja', ident, 'HTTP', estado, salida)

    print(f'\nRESUMEN: {ok} OK / {fallos} FALLIDAS')
    return 0 if fallos == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
