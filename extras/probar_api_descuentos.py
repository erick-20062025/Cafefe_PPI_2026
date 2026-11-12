#!/usr/bin/env python3
"""Pruebas HTTP de Descuento, únicamente para PostgreSQL de desarrollo.

Utiliza un TipoDescuento activo YA EXISTENTE, sin alterarlo. Crea y borra solo
un descuento temporal identificado por una etiqueta única.
No crea relaciones DescuentoProducto ni modifica descuentos existentes.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

ROOT = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'
BASE = ROOT + '/descuentos'
ETIQUETA = 'PRUEBA_REST_DESCUENTO_' + uuid.uuid4().hex[:14].upper()
ZONA = ZoneInfo('America/El_Salvador')
HOY = datetime.now(ZONA).date()
DESDE = (HOY + timedelta(days=3)).isoformat()
HASTA = (HOY + timedelta(days=12)).isoformat()
AYER = (HOY - timedelta(days=2)).isoformat()


def pedir(metodo, ruta='', cuerpo=None, raiz=BASE):
    datos = None
    if cuerpo is not None:
        datos = cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo, ensure_ascii=False).encode('utf-8')
    solicitud = urllib.request.Request(raiz + ruta, data=datos, method=metodo,
        headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(solicitud, timeout=15) as r:
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
    print('Etiqueta temporal:', ETIQUETA)
    print('Solo DESARROLLO. No toca los tipos ni los descuentos existentes.')
    correctas = 0
    fallidas = 0
    omitidas = 0
    creada = None
    comprobaciones = 0

    def verificar(nombre, metodo, ruta, cuerpo, esperado, condicion=None):
        nonlocal correctas, fallidas, comprobaciones
        comprobaciones += 1
        estado, respuesta = pedir(metodo, ruta, cuerpo)
        valida_codigo = esperado not in (400, 404, 409, 422, 500) or (
            isinstance(respuesta, dict) and respuesta.get('codigo') == f'API-{esperado}'
            and bool(respuesta.get('mensaje')))
        bien = (estado == esperado and valida_codigo
                and (condicion is None or condicion(respuesta)))
        if bien:
            correctas += 1
            print(f'OK      {nombre:<46} HTTP {estado}')
        else:
            fallidas += 1
            print(f'REVISAR {nombre:<46} HTTP {estado} (esperado {esperado}) '
                  + str(respuesta)[:220])
        return estado, respuesta

    estado, lista = pedir('GET')
    if estado != 200 or not isinstance(lista, list):
        print('REVISAR API no disponible o PostgreSQL apagado:', estado, lista)
        return 1

    estado_tipo, tipos = pedir('GET', '/tipo-descuento', raiz=ROOT)
    if estado_tipo != 200 or not isinstance(tipos, list):
        print('REVISAR no se pudo consultar /api/tipo-descuento:', estado_tipo, tipos)
        return 1
    activos = [t for t in tipos if isinstance(t, dict)
               and t.get('activo') is True and isinstance(t.get('idTipoDescuento'), str)]
    inactivos = [t for t in tipos if isinstance(t, dict)
                 and t.get('activo') is False and isinstance(t.get('idTipoDescuento'), str)]
    tipo = activos[0]['idTipoDescuento'] if activos else str(uuid.uuid4())

    def datos(**modificaciones):
        res = {'idTipoDescuento': tipo, 'nombre': ETIQUETA,
               'fechaDesde': DESDE, 'fechaHasta': HASTA,
               'observaciones': 'Temporal API REST'}
        res.update(modificaciones)
        return res

    inexistente = str(uuid.uuid4())
    try:
        verificar('GET listar descuentos', 'GET', '', None, 200, lambda x: isinstance(x, list))
        verificar('GET UUID inválido', 'GET', '/uuid-mal', None, 400)
        verificar('GET descuento inexistente', 'GET', '/' + inexistente, None, 404)
        verificar('PUT UUID inválido', 'PUT', '/uuid-mal', datos(), 400)
        verificar('PUT descuento inexistente', 'PUT', '/' + inexistente, datos(), 404)
        verificar('DELETE UUID inválido', 'DELETE', '/uuid-mal', None, 400)
        verificar('DELETE descuento inexistente', 'DELETE', '/' + inexistente, None, 404)

        malas = [
            ('POST sin JSON', b'', 400),
            ('POST JSON malformado', b'{', 400),
            ('POST arreglo JSON', b'[]', 400),
            ('POST objeto vacío', {}, 422),
            ('POST sin tipo de descuento', {'nombre': ETIQUETA, 'fechaDesde': DESDE}, 422),
            ('POST tipo null', datos(idTipoDescuento=None), 422),
            ('POST tipo vacío', datos(idTipoDescuento='  '), 422),
            ('POST tipo número', datos(idTipoDescuento=123), 400),
            ('POST tipo UUID incorrecto', datos(idTipoDescuento='abc'), 400),
            ('POST tipo inexistente', datos(idTipoDescuento=inexistente), 422),
            ('POST sin nombre', {'idTipoDescuento': tipo, 'fechaDesde': DESDE}, 422),
            ('POST nombre null', datos(nombre=None), 422),
            ('POST nombre vacío', datos(nombre='  '), 422),
            ('POST nombre un carácter', datos(nombre='a'), 422),
            ('POST nombre muy largo', datos(nombre='x' * 156), 422),
            ('POST nombre número', datos(nombre=99), 400),
            ('POST nombre carácter control', datos(nombre='prueba\x00mal'), 422),
            ('POST sin fecha desde', {'idTipoDescuento': tipo, 'nombre': ETIQUETA}, 422),
            ('POST fecha desde null', datos(fechaDesde=None), 422),
            ('POST fecha desde vacía', datos(fechaDesde=' '), 422),
            ('POST fecha desde número', datos(fechaDesde=20261112), 400),
            ('POST fecha desde inválida', datos(fechaDesde='2026-13-77'), 400),
            ('POST fecha sin zona', datos(fechaDesde='2026-11-15T12:00:00'), 400),
            ('POST fecha desde pasada', datos(fechaDesde=AYER), 422),
            ('POST fecha hasta anterior', datos(fechaHasta=AYER), 422),
            ('POST fecha hasta inválida', datos(fechaHasta='bad'), 400),
            ('POST fecha hasta número', datos(fechaHasta=3), 400),
            ('POST observaciones número', datos(observaciones=77), 400),
            ('POST observaciones largas', datos(observaciones='x' * 1001), 422),
            ('POST cliente intenta elegir UUID', dict(datos(), idDescuento=inexistente), 400),
            ('POST intenta añadir relaciones', dict(datos(), descuentoProductoCollection=[]), 400),
        ]
        for nombre, cuerpo, esperado in malas:
            verificar(nombre, 'POST', '', cuerpo, esperado)
        if inactivos:
            verificar('POST tipo inactivo', 'POST', '',
                      datos(idTipoDescuento=inactivos[0]['idTipoDescuento']), 422)
        else:
            omitidas += 1
            print('OMITIR  POST tipo inactivo — No hay tipos inactivos para comprobar')

        verificar('POST inválidos no crean descuento', 'GET', '', None, 200,
                 lambda x: isinstance(x, list) and not any(
                     isinstance(d, dict) and d.get('nombre') == ETIQUETA for d in x))

        if not activos:
            omitidas += 1
            print('OMITIR  CRUD positivo — No existe ningún TipoDescuento activo')
        else:
            estado, resultado = verificar('POST crear descuento temporal', 'POST', '', datos(), 201,
                lambda x: isinstance(x, dict) and x.get('nombre') == ETIQUETA
                and x.get('idTipoDescuento') == tipo and bool(x.get('idDescuento')))
            if estado == 201 and isinstance(resultado, dict) and resultado.get('idDescuento'):
                creada = resultado['idDescuento']
            if creada:
                ruta = '/' + creada
                verificar('GET descuento creado', 'GET', ruta, None, 200,
                         lambda x: isinstance(x, dict) and x.get('nombre') == ETIQUETA)
                verificar('GET fechas y tipo correctos', 'GET', ruta, None, 200,
                         lambda x: isinstance(x, dict) and x.get('idTipoDescuento') == tipo
                         and isinstance(x.get('fechaDesde'), str) and x['fechaDesde'].startswith(DESDE)
                         and isinstance(x.get('fechaHasta'), str) and x['fechaHasta'].startswith(HASTA))
                verificar('PUT nombre vacío', 'PUT', ruta, datos(nombre=' '), 422)
                verificar('PUT fecha hasta anterior', 'PUT', ruta, datos(fechaHasta=AYER), 422)
                verificar('PUT impide cambiar el tipo', 'PUT', ruta,
                         datos(idTipoDescuento=inexistente), 409)
                verificar('GET tras PUT rechazados sin cambios', 'GET', ruta, None, 200,
                         lambda x: isinstance(x, dict) and x.get('nombre') == ETIQUETA)
                verificar('PUT modificar descuento', 'PUT', ruta,
                         datos(nombre=ETIQUETA + '_MOD', observaciones='Editado'), 200,
                         lambda x: isinstance(x, dict) and x.get('nombre') == ETIQUETA + '_MOD')
                verificar('PUT permite fecha histórica', 'PUT', ruta,
                         datos(nombre=ETIQUETA + '_MOD', fechaDesde=AYER, fechaHasta=None), 200,
                         lambda x: isinstance(x, dict) and isinstance(x.get('fechaDesde'), str)
                         and x['fechaDesde'].startswith(AYER) and x.get('fechaHasta') is None)
                verificar('GET confirma cambios', 'GET', ruta, None, 200,
                         lambda x: isinstance(x, dict) and x.get('nombre') == ETIQUETA + '_MOD'
                         and x['fechaDesde'].startswith(AYER))
                # El DELETE es la limpieza, no se ejecuta antes del finally.
            else:
                omitidas += 1
                print('OMITIR  PUT y GET positivos — POST no devolvió ID válido')
    finally:
        # La limpieza solo afecta a un descuento con la etiqueta de esta ejecución.
        candidatos = [creada] if creada else []
        if not candidatos:
            estado, descuentos = pedir('GET')
            if estado == 200 and isinstance(descuentos, list):
                candidatos = [d.get('idDescuento') for d in descuentos
                              if isinstance(d, dict) and isinstance(d.get('nombre'), str)
                              and d['nombre'].startswith(ETIQUETA) and isinstance(d.get('idDescuento'), str)]
        for ident in set(candidatos):
            estado, respuesta = pedir('DELETE', '/' + ident)
            if estado == 200:
                correctas += 1
                print('OK      Limpieza: DELETE descuento temporal          HTTP 200')
                estado2, _ = pedir('GET', '/' + ident)
                if estado2 == 404:
                    correctas += 1
                    print('OK      Limpieza: GET confirma eliminación         HTTP 404')
                else:
                    fallidas += 1
                    print('REVISAR GET posterior a limpieza HTTP', estado2)
            else:
                fallidas += 1
                print('REVISAR limpieza manual descuento', ident, estado, respuesta)

    print(f'\nRESUMEN: {correctas} OK / {fallidas} FALLIDAS / {omitidas} OMITIDAS')
    print('Las omitidas NO equivalen a pruebas aprobadas.')
    return 0 if fallidas == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
