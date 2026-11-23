#!/usr/bin/env python3
"""Ensayo acotado de proxy, cookies, permisos y esquema. Solo fixtures desechables."""
import argparse
from http.cookiejar import CookieJar
import json
from pathlib import Path
import subprocess
import time
from urllib.request import Request, build_opener, HTTPCookieProcessor
from urllib.error import HTTPError, URLError

ORIGEN = 'http://localhost:18080'
API = ORIGEN + '/cafefe/api/'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--entorno', type=Path, required=True)
    args = parser.parse_args()
    if json.loads((args.entorno / 'entorno.json').read_text()) != {'tipo': 'tercer-parcial', 'base': 'cafefe_pruebas'}:
        raise SystemExit('Solo se acepta el entorno desechable del preparador')
    for nombre in ('cafefe.backend', 'cafefe.frontend', 'cafefe.db.pruebas'):
        info = json.loads(subprocess.check_output(['docker', 'inspect', nombre], text=True))[0]
        assert info['Config']['Labels'].get('cafefe.ppi115.prueba') == 'tercer-parcial'
    cuentas = json.loads((args.entorno / 'cuentas.json').read_text())

    def esquema():
        return subprocess.check_output(['docker', 'exec', 'cafefe.db.pruebas', 'psql', '-U', 'prueba_oficial',
            '-d', 'cafefe_pruebas', '-Atc', "SELECT table_name,column_name,data_type,is_nullable,COALESCE(column_default,'') FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position"], text=True)

    antes = esquema()
    assert len({fila.split('|')[0] for fila in antes.splitlines()}) == 19
    for prohibida in ('estado_preparacion', 'cliente_web', 'precio_base', 'descuento_aplicado',
                      'promocion_aplicada', 'caracteristicas_historicas', 'producto_historico',
                      'efectivo_recibido', 'cambio_entregado', 'cancelada_por', 'cancelada_en', 'atendido_por'):
        assert not any(f'|{prohibida}|' in fila for fila in antes.splitlines())

    def request(cliente, ruta, metodo='GET', datos=None, origen=ORIGEN):
        req = Request(API + ruta, method=metodo, headers={'Origin': origen, 'Content-Type': 'application/json'},
                      data=json.dumps(datos).encode() if datos is not None else None)
        try:
            with cliente.open(req, timeout=15) as r:
                texto = r.read()
                return r.status, json.loads(texto) if texto else None, r.headers
        except HTTPError as error:
            return error.code, None, error.headers

    anonimo = build_opener(HTTPCookieProcessor(CookieJar()))
    limite = time.monotonic() + 120
    while True:
        try:
            if request(anonimo, 'tipos-producto')[0] == 200:
                break
        except (URLError, TimeoutError):
            pass
        if time.monotonic() >= limite:
            raise SystemExit('El catálogo no quedó disponible; consultar logs del entorno desechable')
        time.sleep(1)
    assert request(anonimo, 'productos')[0] == 200
    privadas = ('cobros/facturas/pendientes', 'facturas', 'pagos/facturas/pendientes', 'empleados', 'empleado-roles', 'roles')
    for ruta in privadas:
        assert request(anonimo, ruta)[0] == 401, ruta
    for rol in ('cajero', 'barista', 'cocinero', 'camarero'):
        jar = CookieJar()
        cliente = build_opener(HTTPCookieProcessor(jar))
        estado, sesion, headers = request(cliente, 'sesion', 'POST', cuentas[rol])
        assert estado == 200 and rol in sesion['roles'], (rol, estado)
        assert 'uuid' not in sesion and 'idEmpleado' not in sesion
        cookie = headers.get('Set-Cookie', '').lower()
        assert 'httponly' in cookie and 'samesite=lax' in cookie and 'path=/cafefe' in cookie
        assert request(cliente, 'sesion')[0] == 200
        for ruta in privadas[:3]:
            assert request(cliente, ruta)[0] == (200 if rol == 'cajero' else 403), (rol, ruta)
        for ruta in privadas[3:]:
            assert request(cliente, ruta)[0] == 403
            assert request(cliente, ruta, 'POST', {})[0] == 403
        assert request(cliente, 'facturas', 'POST', {}, origen='http://ajeno.example')[0] == 403
        assert request(cliente, 'sesion', 'DELETE')[0] == 204
        assert request(cliente, 'sesion')[0] == 401
        print(f'PASS proxy/cookie/sesión/permisos/cierre: {rol}')
    assert esquema() == antes, 'El esquema cambió durante la verificación'
    print('PASS: catálogo anónimo, 19 tablas oficiales sin columnas añadidas, esquema intacto')
    print('No se crearon órdenes, facturas ni abonos; pendiente recorrido integrado de navegador.')


if __name__ == '__main__':
    main()
