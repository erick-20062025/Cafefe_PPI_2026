#!/usr/bin/env python3
"""Pruebas HTTP de Pago sin registrar abonos por defecto.

Opcionalmente, solo en una factura de DESARROLLO creada para pruebas:
  python3 extras/probar_api_pagos.py --factura-prueba UUID --monto 0.01 --confirmar-abono

No crea ni altera facturas del módulo Factura. No detiene PostgreSQL.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid
from decimal import Decimal, InvalidOperation

URL = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/pagos'


def pedir(metodo, ruta, cuerpo=None):
    datos = None if cuerpo is None else (
        cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo).encode('utf-8'))
    solicitud = urllib.request.Request(
        URL + ruta, data=datos, method=metodo,
        headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(solicitud, timeout=12) as respuesta:
            estado, salida = respuesta.status, respuesta.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        estado, salida = e.code, e.read().decode('utf-8', errors='replace')
    except (urllib.error.URLError, TimeoutError) as e:
        return 0, {'error': str(e)}
    try:
        return estado, json.loads(salida) if salida else None
    except ValueError:
        return estado, salida


def ejecutar():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--factura-prueba', help='UUID de factura exclusivamente de desarrollo')
    parser.add_argument('--monto', help='Monto pequeño para abono real de prueba, ej. 0.01')
    parser.add_argument('--confirmar-abono', action='store_true',
                        help='Autoriza una escritura real e irreversible sobre la factura de prueba')
    args = parser.parse_args()
    print('API:', URL)
    print('Por defecto solo consultas y pruebas rechazadas; NO registra pagos válidos.')
    correctas = 0
    fallidas = 0
    omitidas = 0

    def revisar(nombre, ruta, metodo='GET', cuerpo=None, esperado=(200,), comprobar=None):
        nonlocal correctas, fallidas
        estado, respuesta = pedir(metodo, ruta, cuerpo)
        valido = estado in esperado and (comprobar is None or comprobar(respuesta))
        if valido:
            correctas += 1
            print('OK     ', nombre, f'HTTP {estado}')
        else:
            fallidas += 1
            print('REVISAR', nombre, f'HTTP {estado}, esperado {esperado}: {str(respuesta)[:350]}')
        return estado, respuesta

    pendientes_estado, pendientes = revisar(
        'Listar facturas pendientes', '/facturas/pendientes',
        comprobar=lambda v: isinstance(v, list))
    pagadas_estado, pagadas = revisar(
        'Listar facturas pagadas', '/facturas/pagadas',
        comprobar=lambda v: isinstance(v, list))
    if isinstance(pendientes, list) and pendientes:
        print('Facturas pendientes disponibles para consulta (hasta 3):',
              ', '.join(str(f.get('idFactura')) for f in pendientes[:3]))
    if pendientes_estado == 200 or pagadas_estado == 200:
        candidatos = []
        if isinstance(pendientes, list):
            candidatos.extend(pendientes)
        if isinstance(pagadas, list):
            candidatos.extend(pagadas)
        if candidatos:
            id_factura = candidatos[0].get('idFactura')
            if id_factura:
                revisar('Resumen de factura existente', f'/facturas/{id_factura}/resumen',
                        comprobar=lambda v: isinstance(v, dict)
                        and all(k in v for k in ('total', 'abonado', 'saldo', 'completado')))
                revisar('Productos SOLO de esa factura', f'/facturas/{id_factura}/productos',
                        comprobar=lambda v: isinstance(v, list))
                revisar('Abonos SOLO de esa factura', f'/facturas/{id_factura}/abonos',
                        comprobar=lambda v: isinstance(v, list))
            else:
                fallidas += 1
                print('REVISAR Listado sin idFactura')
        else:
            omitidas += 1
            print('OMITIR  Consultar factura real — no hay facturas pendientes ni pagadas')

    falso = str(uuid.uuid4())
    for sufijo in ('resumen', 'productos', 'abonos'):
        revisar('GET UUID inválido ' + sufijo, f'/facturas/no-uuid/{sufijo}', esperado=(400,))
        revisar('GET factura inexistente ' + sufijo, f'/facturas/{falso}/{sufijo}', esperado=(404,))
    ruta = '/facturas/' + falso + '/abonos'
    revisar('POST UUID inválido', '/facturas/no-uuid/abonos', 'POST',
            {'monto': 1, 'tipoPago': 'EFECTIVO'}, (400,))
    revisar('POST sin JSON', ruta, 'POST', b'', (400,))
    revisar('POST JSON malformado', ruta, 'POST', b'{', (400,))
    revisar('POST objeto vacío', ruta, 'POST', {}, (422,))
    revisar('POST monto null', ruta, 'POST', {'monto': None, 'tipoPago': 'EFECTIVO'}, (422,))
    revisar('POST monto cero', ruta, 'POST', {'monto': 0, 'tipoPago': 'EFECTIVO'}, (422,))
    revisar('POST monto negativo', ruta, 'POST', {'monto': -1, 'tipoPago': 'EFECTIVO'}, (422,))
    revisar('POST monto texto', ruta, 'POST', {'monto': '1.00', 'tipoPago': 'EFECTIVO'}, (400,))
    revisar('POST monto con tres decimales', ruta, 'POST', b'{"monto":1.234,"tipoPago":"EFECTIVO"}', (422,))
    revisar('POST método vacío', ruta, 'POST', {'monto': 1, 'tipoPago': '   '}, (422,))
    revisar('POST método inválido', ruta, 'POST', {'monto': 1, 'tipoPago': 'OTRO'}, (422,))
    revisar('POST método número', ruta, 'POST', {'monto': 1, 'tipoPago': 123}, (400,))
    revisar('POST tarjeta sin referencia', ruta, 'POST', {'monto': 1, 'tipoPago': 'TARJETA'}, (422,))
    revisar('POST referencia vacía', ruta, 'POST',
            {'monto': 1, 'tipoPago': 'TRANSFERENCIA', 'referenciaExterna': '   '}, (422,))
    revisar('POST observaciones número', ruta, 'POST',
            {'monto': 1, 'tipoPago': 'EFECTIVO', 'observaciones': 12}, (400,))
    revisar('POST campos no permitidos', ruta, 'POST',
            {'monto': 1, 'tipoPago': 'EFECTIVO', 'estado': 'PAGADA'}, (400,))
    revisar('POST factura inexistente con datos válidos', ruta, 'POST',
            {'monto': 1, 'tipoPago': 'EFECTIVO'}, (404,))

    if args.confirmar_abono:
        if not (args.factura_prueba and args.monto):
            print('ERROR: --confirmar-abono exige --factura-prueba y --monto')
            return 2
        try:
            uuid.UUID(args.factura_prueba)
            importe = Decimal(args.monto)
            if importe <= 0 or importe.as_tuple().exponent < -2:
                raise ValueError('monto no válido')
        except (ValueError, InvalidOperation):
            print('ERROR: UUID o monto de prueba inválidos')
            return 2
        print('AVISO: se registrará un abono REAL sobre factura de desarrollo', args.factura_prueba)
        revisar('POST abono REAL de prueba',
                '/facturas/' + args.factura_prueba + '/abonos',
                'POST', {'monto': float(importe), 'tipoPago': 'EFECTIVO',
                         'observaciones': 'PRUEBA_API_PAGOS_MANUAL'}, (201,),
                comprobar=lambda v: isinstance(v, dict)
                and all(k in v for k in ('total', 'abonado', 'saldo', 'completado')))
    else:
        omitidas += 1
        print('OMITIR  POST positivo REAL — requiere factura de prueba explícita y confirmación')

    print(f'\nRESUMEN: {correctas} OK / {fallidas} FALLIDAS / {omitidas} OMITIDAS')
    print('Las omitidas NO cuentan como aprobadas.')
    return 1 if fallidas else 0


if __name__ == '__main__':
    sys.exit(ejecutar())
