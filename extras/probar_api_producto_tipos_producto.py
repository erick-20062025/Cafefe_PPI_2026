#!/usr/bin/env python3
"""Pruebas REST de ProductoTipoProducto, solo DESARROLLO local.

Estilo de probar_api_descuento_productos.py: OK / REVISAR / OMITIR y etiqueta UUID.
Crea padres SQL nuevos y asociaciones via HTTP; nunca reutiliza datos ajenos.
Lee la conexion JDBC de server.xml sin imprimir credenciales. Requiere psql.
Limpia exclusivamente UUID creados en esta ejecucion, sin cascadas.
"""
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
TABLAS = ('producto', 'orden', 'orden_producto', 'descuento', 'tipo_descuento',
          'descuento_producto', 'tipo_caracteristica', 'caracteristica',
          'producto_caracteristica', 'tipo_producto', 'producto_tipo_producto')


class Pruebas:
    def __init__(self, args):
        self.args = args
        self.base = args.raiz.rstrip('/') + '/producto-tipos-producto'
        self.marca = 'PRUEBA_REST_PRODUCTO_TIPO_' + uuid.uuid4().hex.upper()
        self.ok = self.fallos = self.omitidas = 0
        self.creados = set()
        self.filas = []
        self.baseline = None
        self.producto = self.tipo = str(uuid.uuid4())

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

    def pedir(self, metodo, ruta='', cuerpo=None):
        datos = None if cuerpo is None else (cuerpo if isinstance(cuerpo, bytes) else
                json.dumps(cuerpo, ensure_ascii=False).encode('utf-8'))
        req = urllib.request.Request(self.base + ruta, data=datos, method=metodo,
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
        if metodo == 'POST' and codigo == 201 and isinstance(dato, dict):
            # Incluye creaciones inesperadas de pruebas negativas para limpiarlas.
            self.creados.add(str(uuid.UUID(dato['idProductoTipoProducto'])))
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

    def cuerpo(self, **cambios):
        valor = dict(idProducto=self.producto, idTipoProducto=self.tipo, observaciones=self.marca)
        valor.update(cambios)
        return valor

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
        codigo, _ = self.comprobar('DELETE asociacion propia', 'DELETE', '/' + ident, None, 200,
                                   lambda v: v.get('codigo') == 'API-200')
        if codigo == 200:
            self.creados.discard(ident)
            self.comprobar('GET posterior al DELETE', 'GET', '/' + ident, None, 404)

    def casos(self):
        fake = str(uuid.uuid4())
        self.comprobar('GET general', 'GET', '', None, 200, lambda v: isinstance(v, list))
        for ruta in ('/mal-id', '/productos/mal-id', '/tipos-producto/mal-id'):
            self.comprobar('GET UUID invalido ' + ruta, 'GET', ruta, None, 400)
        self.comprobar('GET inexistente', 'GET', '/' + fake, None, 404)
        self.comprobar('DELETE inexistente', 'DELETE', '/' + fake, None, 404)
        self.comprobar('DELETE UUID invalido', 'DELETE', '/mal-id', None, 400)
        for ruta in ('/productos/' + fake, '/tipos-producto/' + fake):
            self.comprobar('GET filtro sin coincidencias', 'GET', ruta, None, 200, lambda v: v == [])
        for verbo in ('PUT', 'PATCH'):
            for ruta in ('', '/' + fake):
                self.comprobar(verbo + ' no disponible', verbo, ruta, self.cuerpo(), 405)
        for nombre, cuerpo, esperado in [
            ('sin cuerpo', b'', 400), ('JSON roto', b'{', 400), ('array', b'[]', 400),
            ('JSON escalar', b'true', 400), ('JSON excesivo', b' '*8193, 400),
            ('objeto vacio', {}, 422),
            ('observaciones numero', self.cuerpo(observaciones=1), 400),
            ('observaciones largas', self.cuerpo(observaciones='x'*1001), 422),
            ('observaciones control', self.cuerpo(observaciones='x\x00'), 422),
        ]:
            self.comprobar('POST '+nombre, 'POST', '', cuerpo, esperado)
        for campo in ('idProducto', 'idTipoProducto'):
            self.comprobar('POST falta '+campo, 'POST', '', {k:v for k,v in self.cuerpo().items() if k != campo}, 422)
            for valor, codigo in ((None,422), ('',422), (1,400), (True,400), ('1-1-1-1-1',400), (fake,422)):
                self.comprobar('POST '+campo+' '+str(valor), 'POST', '', self.cuerpo(**{campo:valor}), codigo)
        for campo in ('idProductoTipoProducto', 'fechaCreacion', 'activo', 'producto', 'productoTipoProductoCollection'):
            self.comprobar('POST campo prohibido '+campo, 'POST', '', self.cuerpo(**{campo:None}), 400)
        inactivo = self.insertar('tipo_producto', nombre=self.marca, activo=False)
        self.comprobar('POST tipo inactivo', 'POST', '', self.cuerpo(idTipoProducto=inactivo), 422)
        # El producto de prueba es inactivo: debe poder asociarse.
        antes = datetime.now(timezone.utc)
        codigo, dto = self.comprobar('POST producto inactivo y tipo activo', 'POST', '', self.cuerpo(observaciones='x'*1000), 201)
        if codigo != 201:
            raise RuntimeError('No se creo la asociacion temporal')
        ident = str(uuid.UUID(dto['idProductoTipoProducto']))
        self.resultado('DTO plano y referencias correctas', set(dto) == {
            'idProductoTipoProducto','idProducto','idTipoProducto','fechaCreacion','observaciones'}
            and dto['idProducto'] == self.producto and dto['idTipoProducto'] == self.tipo)
        fecha = datetime.fromisoformat(dto['fechaCreacion'])
        self.resultado('Fecha del servidor compatible con instante actual', antes <= fecha <= datetime.now(timezone.utc))
        self.resultado('Observaciones limite conservadas', dto['observaciones'] == 'x'*1000)
        self.comprobar('GET por UUID', 'GET', '/'+ident, None, 200,
                       lambda v: v['idProductoTipoProducto'] == ident)
        for ruta in ('/productos/'+self.producto, '/tipos-producto/'+self.tipo):
            self.comprobar('GET filtro devuelve solo asociacion propia', 'GET', ruta, None, 200,
                           lambda v: len(v) == 1 and v[0]['idProductoTipoProducto'] == ident)
        self.comprobar('POST duplicado', 'POST', '', self.cuerpo(), 409)
        consulta = (f"SELECT row_to_json(r) FROM producto r WHERE id_producto='{self.producto}';"
                    f"SELECT row_to_json(r) FROM tipo_producto r WHERE id_tipo_producto='{self.tipo}';")
        padres = self.sql(consulta)
        self.borrar(ident)
        self.resultado('DELETE conserva ambos padres completos', padres == self.sql(consulta))
        self.comprobar('DELETE repetido', 'DELETE', '/'+ident, None, 404)
        # Ambas solicitudes salen de la barrera a la vez, sobre la misma pareja.
        for ronda in range(3):
            barrera = Barrier(2)
            def crear_concurrente(_):
                barrera.wait(timeout=10)
                return self.pedir('POST', '', self.cuerpo(observaciones=None))
            with ThreadPoolExecutor(max_workers=2) as pool:
                respuestas = list(pool.map(crear_concurrente, range(2)))
            self.resultado(f'POST concurrentes ronda {ronda+1}: un 201 y un 409',
                           sorted(c for c,_ in respuestas) == [201,409], respuestas)
            self.resultado(f'SQL ronda {ronda+1}: solo una pareja', self.sql(
                f"SELECT count(*) FROM producto_tipo_producto WHERE id_producto='{self.producto}' AND id_tipo_producto='{self.tipo}';") == '1')
            for codigo, dato in respuestas:
                if codigo == 201:
                    self.resultado('Observaciones null conservadas', dato.get('observaciones') is None)
                    self.borrar(str(uuid.UUID(dato['idProductoTipoProducto'])))

    def ejecutar(self):
        print('API:', self.base, '\nEtiqueta:', self.marca, flush=True)
        try:
            codigo, _ = self.pedir('GET')
            if codigo != 200:
                raise RuntimeError(f'API no disponible: HTTP {codigo}')
            self.baseline = self.huella()
            self.producto = self.insertar('producto', nombre=self.marca, activo=False,
                                         precio_sugerido=1, comentarios=self.marca)
            self.tipo = self.insertar('tipo_producto', nombre=self.marca, activo=True)
            self.casos()
        except Exception as e:
            self.resultado('Ejecucion completa', False, str(e))
            self.omitir('Casos posteriores al error; revisar registro')
        finally:
            try:
                for ident in list(self.creados):
                    self.borrar(ident)
                if self.filas:
                    self.limpiar_filas()
                if self.baseline is not None:
                    self.resultado('Contenido de las 11 tablas identico al inicial', self.baseline == self.huella())
            except Exception as e:
                self.resultado('Limpieza completa', False, str(e))
            if self.creados or self.filas:
                print('PENDIENTE limpieza:', sorted(self.creados), self.filas)
            print(f'RESUMEN: {self.ok} OK / {self.fallos} FALLIDAS / {self.omitidas} PENDIENTES', flush=True)
        return int(bool(self.fallos or self.omitidas))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raiz', '--base-url', dest='raiz', default=DEFAULT_ROOT)
    args = parser.parse_args()
    from urllib.parse import urlparse
    if urlparse(args.raiz).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Solo se permiten pruebas locales de desarrollo.')
    return Pruebas(args).ejecutar()


if __name__ == '__main__':
    sys.exit(main())
