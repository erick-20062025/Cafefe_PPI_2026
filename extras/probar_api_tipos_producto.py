#!/usr/bin/env python3
"""Pruebas REST de TipoProducto, solo DESARROLLO local.

Estilo de probar_api_descuento_productos.py: OK / REVISAR / OMITIR y etiqueta UUID.
--integracion-db crea una relacion SQL con producto nuevo; nunca reutiliza datos ajenos.
Lee la conexion JDBC de server.xml sin imprimir credenciales. Requiere psql.
Limpia exclusivamente UUID creados en esta ejecucion, sin cascadas.
"""
import argparse
import json
import os
from pathlib import Path
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
        self.base = args.raiz.rstrip('/') + '/tipos-producto'
        self.marca = 'PRUEBA_REST_TIPO_PRODUCTO_' + uuid.uuid4().hex.upper()
        self.ok = self.fallos = self.omitidas = 0
        self.creados = set()
        self.filas = []
        self.baseline = None

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
            self.creados.add(str(uuid.UUID(dato['idTipoProducto'])))
        return codigo, dato

    def comprobar(self, nombre, metodo, ruta, cuerpo, esperado, condicion=None):
        codigo, dato = self.pedir(metodo, ruta, cuerpo)
        envoltura = esperado < 400 or (isinstance(dato, dict)
            and dato.get('codigo') == f'API-{esperado}' and bool(dato.get('mensaje'))
            and isinstance(dato.get('detalle'), str))
        bien = codigo == esperado and envoltura
        if bien and condicion:
            bien = condicion(dato)
        self.resultado(nombre, bien, f'HTTP {codigo}, esperado {esperado}; {dato}')
        return codigo, dato

    def cuerpo(self, **cambios):
        valor = dict(nombre=self.marca, activo=True, observaciones=self.marca)
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

    def negativas(self):
        fake = '/' + str(uuid.uuid4())
        self.comprobar('GET listado', 'GET', '', None, 200, lambda v: isinstance(v, list))
        for metodo in ('GET', 'PUT', 'DELETE'):
            cuerpo = self.cuerpo() if metodo == 'PUT' else None
            self.comprobar(metodo + ' UUID invalido', metodo, '/mal-id', cuerpo, 400)
            self.comprobar(metodo + ' inexistente', metodo, fake, cuerpo, 404)
        casos = [
            ('sin cuerpo', b'', 400), ('JSON malformado', b'{', 400),
            ('arreglo', b'[]', 400), ('booleano', b'true', 400),
            ('JSON >8192', b' ' * 8193, 400), ('objeto vacio', {}, 422),
            ('nombre null', self.cuerpo(nombre=None), 422),
            ('nombre blanco', self.cuerpo(nombre=' \t '), 422),
            ('nombre numero', self.cuerpo(nombre=42), 400),
            ('nombre >155', self.cuerpo(nombre='x' * 156), 422),
            ('activo texto', self.cuerpo(activo='true'), 400),
            ('activo null', self.cuerpo(activo=None), 422),
            ('observaciones numero', self.cuerpo(observaciones=123), 400),
            ('observaciones >1000', self.cuerpo(observaciones='x' * 1001), 422),
            ('observaciones control', self.cuerpo(observaciones='a\x00b'), 422),
            ('ID del cliente', self.cuerpo(idTipoProducto=str(uuid.uuid4())), 400),
            ('coleccion JPA', self.cuerpo(productoTipoProductoCollection=[]), 400),
        ]
        for nombre, cuerpo, esperado in casos:
            self.comprobar('POST ' + nombre, 'POST', '', cuerpo, esperado)
        return casos

    def crear(self, nombre):
        codigo, dato = self.comprobar(nombre, 'POST', '', self.cuerpo(), 201,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca and set(v) <=
            {'idTipoProducto', 'nombre', 'activo', 'observaciones'})
        if codigo != 201 or not isinstance(dato, dict) or not dato.get('idTipoProducto'):
            raise RuntimeError('No se obtuvo tipo temporal; se detienen las escrituras.')
        return str(uuid.UUID(dato['idTipoProducto']))

    def positivas(self, casos):
        ident = self.crear('POST tipo temporal')
        duplicado = self.crear('POST permite nombre repetido')
        self.resultado('Nombres repetidos tienen UUID distintos', ident != duplicado)
        ruta = '/' + ident
        self.comprobar('GET tipo creado', 'GET', ruta, None, 200,
                       lambda v: v.get('nombre') == self.marca)
        for nombre, cuerpo, esperado in casos:
            self.comprobar('PUT ' + nombre, 'PUT', ruta, cuerpo, esperado)
        self.comprobar('GET negativos no alteraron datos', 'GET', ruta, None, 200,
                       lambda v: v.get('nombre') == self.marca and v.get('activo') is True)
        self.comprobar('PUT un caracter, inactivo y observaciones limite', 'PUT', ruta,
                       self.cuerpo(nombre='A', activo=False, observaciones='x' * 1000), 200,
                       lambda v: v.get('activo') is False and v.get('nombre') == 'A')
        self.comprobar('GET confirma estado y observaciones', 'GET', ruta, None, 200,
                       lambda v: v.get('activo') is False and len(v.get('observaciones', '')) == 1000)
        self.comprobar('PUT observaciones opcionales y nombre limite', 'PUT', ruta,
                       dict(nombre='x' * 155, activo=True), 200,
                       lambda v: v.get('observaciones') is None)
        self.borrar(ident)
        self.comprobar('DELETE repetido', 'DELETE', ruta, None, 404)
        self.borrar(duplicado)

    def borrar(self, ident):
        codigo, _ = self.comprobar('DELETE tipo propio sin relaciones', 'DELETE', '/' + ident, None, 200,
                                   lambda v: v.get('codigo') == 'API-200')
        if codigo == 200:
            self.creados.discard(ident)
            self.comprobar('GET tras DELETE', 'GET', '/' + ident, None, 404)

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

    def relaciones(self):
        tipo = self.crear('POST tipo para asociacion aislada')
        producto = self.insertar('producto', nombre=self.marca, activo=True,
                                 precio_sugerido=1, comentarios=self.marca)
        relacion = self.insertar('producto_tipo_producto', id_producto=producto,
                                 id_tipo_producto=tipo, observaciones=self.marca)
        consulta_relacion = (f"SELECT row_to_json(r) FROM producto r WHERE id_producto='{producto}';"
                            f"SELECT row_to_json(r) FROM producto_tipo_producto r WHERE id_producto_tipo_producto='{relacion}';")
        consulta_tipo = f"SELECT row_to_json(r) FROM tipo_producto r WHERE id_tipo_producto='{tipo}';"
        antes = self.sql(consulta_tipo + consulta_relacion)
        self.comprobar('DELETE tipo activo asociado', 'DELETE', '/' + tipo, None, 409)
        self.resultado('Tipo activo, producto y asociacion intactos',
                       antes == self.sql(consulta_tipo + consulta_relacion))
        antes_relacion = self.sql(consulta_relacion)
        self.comprobar('PUT permite desactivar tipo asociado', 'PUT', '/' + tipo,
                       self.cuerpo(activo=False), 200, lambda v: v.get('activo') is False)
        self.resultado('Desactivar conserva producto y asociacion',
                       antes_relacion == self.sql(consulta_relacion))
        self.comprobar('GET confirma tipo inactivo asociado', 'GET', '/' + tipo, None, 200,
                       lambda v: v.get('activo') is False)
        antes = self.sql(consulta_tipo + consulta_relacion)
        self.comprobar('DELETE tipo inactivo asociado', 'DELETE', '/' + tipo, None, 409)
        self.resultado('Tipo inactivo, producto y asociacion intactos',
                       antes == self.sql(consulta_tipo + consulta_relacion))
        self.limpiar_filas()
        self.borrar(tipo)

    def limpiar_filas(self, inicio=0):
        filas = self.filas[inicio:]
        # Transaccion completa: si aparece una referencia ajena, RESTRICT cancela la limpieza.
        self.sql('BEGIN;' + ''.join(f"DELETE FROM {t} WHERE id_{t}='{i}';" for t, i in reversed(filas)) + 'COMMIT;')
        self.resultado('Limpieza SQL limitada a UUID propios', all(
            self.sql(f"SELECT count(*) FROM {t} WHERE id_{t}='{i}';") == '0' for t, i in filas))
        del self.filas[inicio:]

    def ejecutar(self):
        print('API:', self.base, '\nEtiqueta:', self.marca, flush=True)
        try:
            codigo, _ = self.pedir('GET')
            if codigo != 200:
                raise RuntimeError(f'API no disponible: HTTP {codigo}')
            if self.args.integracion_db:
                self.baseline = self.huella()
            casos = self.negativas()
            self.positivas(casos)
            if self.args.integracion_db:
                self.relaciones()
            else:
                self.omitir('409 con asociacion activa e inactiva: requiere --integracion-db')
        except Exception as e:
            self.resultado('Ejecucion completa', False, str(e))
            self.omitir('Casos posteriores al error; revisar salida')
        finally:
            try:
                if self.filas:
                    self.limpiar_filas()
                for ident in list(self.creados):
                    self.borrar(ident)
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
    parser.add_argument('--integracion-db', action='store_true')
    args = parser.parse_args()
    from urllib.parse import urlparse
    if urlparse(args.raiz).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Solo se permiten pruebas locales de desarrollo.')
    return Pruebas(args).ejecutar()


if __name__ == '__main__':
    sys.exit(main())
