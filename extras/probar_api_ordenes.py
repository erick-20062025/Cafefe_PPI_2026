#!/usr/bin/env python3
"""Pruebas HTTP conservadoras. No crea órdenes válidas ni elimina registros."""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/ordenes'
CAMARERO = str(uuid.uuid4())  # intencionalmente inexistente
PRODUCTO = str(uuid.uuid4())
OK = 0
TOTAL = 0


def solicitar(metodo, ruta='', dato=None):
    body = None if dato is None else (dato if isinstance(dato, str) else json.dumps(dato, ensure_ascii=False))
    req = urllib.request.Request(BASE + ruta, data=body.encode() if body is not None else None,
                                 method=metodo, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=20) as respuesta:
            return respuesta.status, respuesta.read().decode('utf-8', errors='replace')
    except urllib.error.HTTPError as ex:
        return ex.code, ex.read().decode('utf-8', errors='replace')


def probar(nombre, metodo, ruta, dato, estados):
    global OK, TOTAL
    TOTAL += 1
    try:
        estado, cuerpo = solicitar(metodo, ruta, dato)
        valido = estado in estados
        if estado >= 400:
            try:
                valor = json.loads(cuerpo)
                valido = valido and isinstance(valor, dict) and 'codigo' in valor and 'mensaje' in valor
            except ValueError:
                valido = False
        if valido:
            OK += 1
        print(('OK     ' if valido else 'REVISAR') + f' {nombre:51s} HTTP {estado} (esperado {estados})')
        if not valido: print('       ', cuerpo[:250])
    except Exception as ex:
        print('ERROR  ', nombre, ex)


def ejemplo():
    return {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': PRODUCTO}]}


if __name__ == '__main__':
    if len(sys.argv) > 1:
        BASE = sys.argv[1].rstrip('/')
    print('API:', BASE)
    print('Pruebas negativas: no se crean órdenes válidas ni se cambian registros existentes.')
    probar('Listar cabeceras', 'GET', '', None, {200})
    probar('Listar órdenes pendientes', 'GET', '/pendientes', None, {200})
    probar('GET UUID inválido', 'GET', '/id-invalido', None, {400})
    probar('GET UUID inexistente', 'GET', '/' + str(uuid.uuid4()), None, {404})
    probar('POST sin JSON', 'POST', '', '', {400})
    probar('POST JSON malformado', 'POST', '', '{mal', {400})
    probar('POST objeto vacío', 'POST', '', '{}', {422})
    probar('POST arreglo en lugar de objeto', 'POST', '', '[]', {400})
    probar('POST sin lista de productos', 'POST', '', {'idEmpleadoRol': CAMARERO}, {422})
    probar('POST productos vacíos', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': []}, {422})
    probar('POST producto inválido', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': 'x'}]}, {400})
    probar('POST cantidad cero', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': PRODUCTO, 'cantidad': 0}]}, {422})
    probar('POST cantidad negativa', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': PRODUCTO, 'cantidad': -3}]}, {422})
    probar('POST cantidad decimal', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': PRODUCTO, 'cantidad': 1.5}]}, {400})
    probar('POST cantidad texto', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': PRODUCTO, 'cantidad': '2'}]}, {400})
    probar('POST precio manipulado', 'POST', '', {'idEmpleadoRol': CAMARERO, 'productos': [{'idProducto': PRODUCTO, 'precio': 0.01}]}, {400})
    probar('POST fecha sin zona horaria', 'POST', '', {'idEmpleadoRol': CAMARERO, 'fechaCreacion': '2026-11-12', 'productos': [{'idProducto': PRODUCTO}]}, {400})
    probar('POST camarero no existente', 'POST', '', ejemplo(), {422})
    probar('POST público sin cliente', 'POST', '/publicas', {'productos': [{'idProducto': PRODUCTO}]}, {422})
    probar('POST público intenta elegir camarero', 'POST', '/publicas', {'nombreCliente': 'Ana', **ejemplo()}, {400})
    probar('DELETE UUID incorrecto', 'DELETE', '/id-invalido', None, {400})
    probar('DELETE orden inexistente', 'DELETE', '/' + str(uuid.uuid4()), None, {404})
    print(f'\nRESUMEN: {OK}/{TOTAL} comprobaciones correctas')
    sys.exit(0 if OK == TOTAL else 1)
