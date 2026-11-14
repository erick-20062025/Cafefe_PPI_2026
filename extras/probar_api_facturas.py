#!/usr/bin/env python3
"""Pruebas REST y PostgreSQL de Factura con registros temporales propios."""
import argparse
import json
import os
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from datetime import datetime, timezone
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET

DEFAULT_ROOT = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'
CONFIG = Path(__file__).resolve().parents[1] / 'src/main/liberty/config/server.xml'
TABLAS = ('producto', 'orden', 'orden_producto', 'caja', 'empleado', 'rol',
          'empleado_rol', 'factura', 'factura_orden_producto', 'pago', 'pago_detalle')


class Pruebas:
    def __init__(self, args):
        self.args = args
        self.base = args.raiz.rstrip('/') + '/facturas'
        self.marca = 'PRUEBA_REST_FACTURA_' + uuid.uuid4().hex.upper()
        self.ok = self.fallos = self.omitidas = 0
        self.creados = set()
        self.filas = []
        self.baseline = None
        self.facturas = set()
        self.raiz = args.raiz.rstrip("/")

    def resultado(self, nombre, correcto, detalle=''):
        if correcto:
            self.ok += 1
        else:
            self.fallos += 1
        print(('OK      ' if correcto else 'REVISAR ') + nombre +
              (': ' + str(detalle)[:300] if not correcto else ''), flush=True)

    def omitir(self, nombre):
        self.omitidas += 1
        print('OMITIR  ' + nombre, flush=True)

    def pedir(self, metodo, ruta='', cuerpo=None, *, raiz=None):
        datos = None if cuerpo is None else (cuerpo if isinstance(cuerpo, bytes) else
                json.dumps(cuerpo, ensure_ascii=False).encode('utf-8'))
        req = urllib.request.Request((raiz or self.base) + ruta, data=datos, method=metodo,
                                    headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                codigo, texto = r.status, r.read().decode('utf-8', 'replace')
        except urllib.error.HTTPError as e:
            codigo, texto = e.code, e.read().decode('utf-8', 'replace')
        except (urllib.error.URLError, TimeoutError) as e:
            return 0, {'conexion': str(e)}
        try:
            dato = json.loads(texto) if texto else None
        except ValueError:
            dato = texto[:300]
        if raiz is None and metodo == 'POST' and codigo == 201 and isinstance(dato, dict):
            # Incluye creaciones inesperadas de pruebas negativas para limpiarlas.
            self.creados.add(str(uuid.UUID(dato['idFactura'])))
            self.facturas.add(str(uuid.UUID(dato['idFactura'])))
        return codigo, dato

    def comprobar(self, nombre, metodo, ruta, cuerpo, esperado, condicion=None):
        codigo, dato = self.pedir(metodo, ruta, cuerpo)
        envoltura = esperado == 405 or esperado < 400 or (isinstance(dato, dict)
            and dato.get('codigo') == f'API-{esperado}' and bool(dato.get('mensaje'))
            and isinstance(dato.get('detalle'), str))
        bien = codigo == esperado and envoltura
        if bien and condicion:
            bien = condicion(dato)
        self.resultado(nombre, bien, f'HTTP {codigo}, esperado {esperado}; {dato}')
        return codigo, dato

    def sql(self, consulta):
        p = ET.parse(CONFIG).find('.//properties.postgresql').attrib
        if p['serverName'] not in ('localhost', '127.0.0.1', '::1'):
            raise RuntimeError('Este script solo admite PostgreSQL local.')
        env = dict(os.environ, PGPASSWORD=p['password'], PGCONNECT_TIMEOUT='5')
        r = subprocess.run(['psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1',
                            '-h', p['serverName'], '-p', p['portNumber'], '-U', p['user'],
                            '-d', p['databaseName']], input=consulta, text=True,
                           capture_output=True, timeout=25, env=env)
        if r.returncode:
            raise RuntimeError('PostgreSQL: ' + r.stderr[:500])
        return r.stdout.strip()

    def huella(self):
        # Solo devuelve hashes, nunca contenido de registros existentes.
        consultas = [f"SELECT '{t}', count(*), md5(COALESCE(string_agg(row_to_json(r)::text, '' ORDER BY id_{t}), '')) FROM {t} r;"
                     for t in TABLAS]
        return self.sql('BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;'
                        + ''.join(consultas) + 'COMMIT;')

    def insertar(self, tabla, **campos):
        # Tablas y columnas son constantes del script, valores solo UUID/marca propios.
        if tabla not in TABLAS:
            raise ValueError('Tabla no permitida')
        ident = str(uuid.uuid4())
        valores = {f'id_{tabla}': ident, **campos}
        def literal(v):
            if isinstance(v, bool):
                return 'true' if v else 'false'
            return "'" + str(v).replace("'", "''") + "'"
        self.sql(f"INSERT INTO {tabla} ({','.join(valores)}) VALUES ({','.join(literal(v) for v in valores.values())});")
        self.filas.append((tabla, ident))
        return ident

    def limpiar_filas(self, inicio=0):
        filas = self.filas[inicio:]
        # Transaccion completa: si aparece una referencia ajena, RESTRICT cancela la limpieza.
        self.sql('BEGIN;' + ''.join(f"DELETE FROM {t} WHERE id_{t}='{i}';" for t, i in reversed(filas)) + 'COMMIT;')
        self.resultado('Limpieza SQL limitada a UUID propios', all(
            self.sql(f"SELECT count(*) FROM {t} WHERE id_{t}='{i}';") == '0' for t, i in filas))
        del self.filas[inicio:]

    def borrar(self, ident):
        codigo, _ = self.comprobar('DELETE factura propia', 'DELETE', '/' + ident, None, 200,
                                   lambda v: v.get('codigo') == 'API-200')
        if codigo == 200:
            self.creados.discard(ident)
            self.comprobar('GET posterior al DELETE', 'GET', '/' + ident, None, 404)

    def casos(self):
        caja = self.insertar('caja', nombre=self.marca, activo=True)
        rol = self.insertar('rol', nombre=self.marca, activo=True)
        empleado = self.insertar('empleado', nombre=self.marca, apellido='Prueba', activo=True)
        asignacion = self.insertar('empleado_rol', id_rol=rol, id_empleado=empleado, activo=True)
        orden = self.insertar('orden', id_empleado_rol=asignacion)
        otra = self.insertar('orden', id_empleado_rol=asignacion)
        producto = self.insertar('producto', nombre=self.marca, activo=True, precio_sugerido=10)
        primero = self.insertar('orden_producto', id_orden=orden, id_producto=producto, precio=10)
        segundo = self.insertar('orden_producto', id_orden=orden, id_producto=producto, precio=10)
        ajeno = self.insertar('orden_producto', id_orden=otra, id_producto=producto, precio=10)
        fila = {'idOrdenProducto': primero, 'precio': 10.25}
        cuerpo = {'idCaja': caja, 'idEmpleadoRol': asignacion, 'observaciones': self.marca,
                  'productos': [fila]}
        falta = '/' + str(uuid.uuid4())
        self.comprobar('GET listado', 'GET', '', None, 200, lambda v: isinstance(v, list))
        self.comprobar('GET UUID inválido', 'GET', '/mal', None, 400)
        self.comprobar('GET factura inexistente', 'GET', falta, None, 404)
        self.comprobar('PUT factura inexistente', 'PUT', falta, cuerpo, 404)
        self.comprobar('DELETE factura inexistente', 'DELETE', falta, None, 404)
        self.comprobar('POST JSON inválido', 'POST', '', b'{', 400)
        self.comprobar('POST estado prohibido', 'POST', '', dict(cuerpo, estado='PAGADA'), 400)
        for nombre, datos in (
                ('caja inexistente', dict(cuerpo, idCaja=falta[1:])),
                ('empleado inexistente', dict(cuerpo, idEmpleadoRol=falta[1:])),
                ('sin detalles', dict(cuerpo, productos=[])),
                ('detalle inexistente', dict(cuerpo, productos=[dict(fila, idOrdenProducto=falta[1:])])),
                ('detalle duplicado', dict(cuerpo, productos=[fila, fila])),
                ('órdenes mezcladas', dict(cuerpo, productos=[fila, dict(fila, idOrdenProducto=ajeno)])),
                ('total cero', dict(cuerpo, productos=[dict(fila, precio=0)])),
                ('precio negativo', dict(cuerpo, productos=[dict(fila, precio=-1)])),
                ('tres decimales', dict(cuerpo, productos=[dict(fila, precio=1.002)]))):
            self.comprobar('POST ' + nombre, 'POST', '', datos, 422)
        self.resultado('Altas rechazadas no dejaron cabeceras',
                       self.sql(f"SELECT count(*) FROM factura WHERE id_caja='{caja}';") == '0')
        estado, dto = self.comprobar('POST factura pendiente', 'POST', '',
                                     dict(cuerpo, cliente='Cliente temporal'), 201,
                                     lambda v: v['estado'] == 'PENDIENTE' and bool(v['fechaFacturacion']))
        if estado != 201:
            raise RuntimeError('No se creó la factura de prueba')
        ident = str(uuid.UUID(dto['idFactura']))
        ruta = '/' + ident
        detalle = dto['productos'][0]['idFacturaOrdenProducto']
        self.comprobar('GET detalle generado', 'GET', ruta, None, 200,
                       lambda v: v['productos'][0]['idFacturaOrdenProducto'] == detalle)
        self.comprobar('PUT conserva cliente y UUID del detalle', 'PUT', ruta, cuerpo, 200,
                       lambda v: v['cliente'] == 'Cliente temporal'
                       and v['productos'][0]['idFacturaOrdenProducto'] == detalle)
        self.comprobar('POST fila ya facturada', 'POST', '', cuerpo, 409)
        self.comprobar('PUT inválido revierte cabecera y detalles', 'PUT', ruta,
                       dict(cuerpo, observaciones='no guardar', productos=[fila, dict(fila, idOrdenProducto=ajeno)]), 422)
        self.comprobar('GET después de PUT inválido', 'GET', ruta, None, 200,
                       lambda v: v['observaciones'] == self.marca and len(v['productos']) == 1)
        self.borrar(ident)
        self.resultado('DELETE conserva OrdenProducto', self.sql(
            f"SELECT count(*) FROM orden_producto WHERE id_orden_producto='{primero}';") == '1')

        barrera = Barrier(2)
        def crear(_):
            barrera.wait(timeout=10)
            return self.pedir('POST', '', cuerpo)
        with ThreadPoolExecutor(max_workers=2) as executor:
            resultados = list(executor.map(crear, range(2)))
        self.resultado('Dos facturas concurrentes para una fila dan 201 y 409',
                       sorted(codigo for codigo, _ in resultados) == [201, 409], resultados)
        self.resultado('Solo existe una asociación para la fila', self.sql(
            f"SELECT count(*) FROM factura_orden_producto WHERE id_orden_producto='{primero}';") == '1')
        for codigo, dto in resultados:
            if codigo == 201:
                ident = str(uuid.UUID(dto['idFactura']))
                ruta = '/' + ident
                cobro = '/pagos/facturas' + ruta + '/abonos'
                pago, respuesta = self.pedir('POST', cobro, {'tipoPago': 'EFECTIVO', 'monto': 1}, raiz=self.raiz)
                self.resultado('Pago registra abono parcial', pago == 201, respuesta)
                self.comprobar('PUT con abono bloqueado', 'PUT', ruta, cuerpo, 409)
                self.comprobar('DELETE con abono bloqueado', 'DELETE', ruta, None, 409)
                pago, respuesta = self.pedir('POST', cobro, {'tipoPago': 'EFECTIVO', 'monto': 9.25}, raiz=self.raiz)
                self.resultado('Pago completa el saldo', pago == 201, respuesta)
                self.comprobar('GET factura pagada por Pago', 'GET', ruta, None, 200,
                               lambda v: v['estado'] == 'PAGADA')
                self.comprobar('DELETE factura pagada', 'DELETE', ruta, None, 409)

        otro_cuerpo = dict(cuerpo, productos=[dict(fila, idOrdenProducto=segundo)])
        estado, dto = self.comprobar('POST segunda línea de la orden', 'POST', '', otro_cuerpo, 201)
        if estado == 201:
            ident = str(uuid.UUID(dto['idFactura']))
            self.insertar('pago', id_factura=ident, estado='ANULADO')
            self.comprobar('PUT ignora pago anulado', 'PUT', '/' + ident, otro_cuerpo, 200)
            self.comprobar('DELETE bloquea cabecera de pago anulada', 'DELETE', '/' + ident, None, 409)

    def ejecutar(self):
        print('API:', self.base, '\nEtiqueta:', self.marca, flush=True)
        try:
            codigo, _ = self.pedir('GET')
            if codigo != 200:
                raise RuntimeError(f'API no disponible, HTTP {codigo}')
            self.baseline = self.huella()
            self.casos()
        except Exception as ex:
            self.resultado('Ejecución completa', False, ex)
        finally:
            try:
                # El cobro impide el borrado por REST. Limpiar únicamente facturas
                # propias devueltas por POST, y sus hijos, antes de los padres.
                for ident in self.facturas:
                    self.sql(f"""BEGIN;
                        DELETE FROM pago_detalle WHERE id_pago IN (SELECT id_pago FROM pago WHERE id_factura='{ident}');
                        DELETE FROM pago WHERE id_factura='{ident}';
                        DELETE FROM factura_orden_producto WHERE id_factura='{ident}';
                        DELETE FROM factura WHERE id_factura='{ident}';
                        COMMIT;""")
                self.filas = [(t, i) for t, i in self.filas if t != 'pago']
                if self.filas:
                    self.limpiar_filas()
                if self.baseline is not None:
                    self.resultado('Contenido de las 11 tablas idéntico al inicial', self.baseline == self.huella())
            except Exception as ex:
                self.resultado('Limpieza completa', False, ex)
                print('UUID de facturas y filas de prueba:', self.facturas, self.filas)
            print(f'RESUMEN: {self.ok} OK / {self.fallos} FALLIDAS / {self.omitidas} PENDIENTES')
        return int(bool(self.fallos or self.omitidas))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raiz', default=DEFAULT_ROOT)
    args = parser.parse_args()
    if urllib.parse.urlparse(args.raiz).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Use la API local de desarrollo')
    sys.exit(Pruebas(args).ejecutar())
