#!/usr/bin/env python3
"""Pruebas REST y PostgreSQL del catálogo de características."""
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
TABLAS = ('producto', 'tipo_caracteristica', 'caracteristica', 'producto_caracteristica')


class Pruebas:
    recurso = 'caracteristicas'
    clave = 'idCaracteristica'
    def __init__(self, args):
        self.args = args
        self.base = args.raiz.rstrip('/') + '/' + self.recurso
        self.marca = 'PRUEBA_REST_CARACTERISTICA_' + uuid.uuid4().hex.upper()
        self.ok = self.fallos = self.omitidas = 0
        self.creados = set()
        self.filas = []
        self.baseline = None
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
            self.creados.add(str(uuid.UUID(dato[self.clave])))
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
        codigo, _ = self.comprobar('DELETE registro propio', 'DELETE', '/' + ident, None, 200,
                                   lambda v: v.get('codigo') == 'API-200')
        if codigo == 200:
            self.creados.discard(ident)
            self.comprobar('GET posterior al DELETE', 'GET', '/' + ident, None, 404)

    def casos(self):
        tipo = self.insertar('tipo_caracteristica', nombre=self.marca, activo=True)
        inactivo = self.insertar('tipo_caracteristica', nombre=self.marca, activo=False)
        cuerpo = dict(idTipoCaracteristica=tipo, nombre=self.marca, activo=True)
        falta = '/' + str(uuid.uuid4())
        self.comprobar('GET listado', 'GET', '', None, 200, lambda v: isinstance(v, list))
        self.comprobar('GET UUID inválido', 'GET', '/mal', None, 400)
        self.comprobar('GET inexistente', 'GET', falta, None, 404)
        self.comprobar('DELETE inexistente', 'DELETE', falta, None, 404)
        self.comprobar('PUT inexistente', 'PUT', falta, cuerpo, 404)
        for nombre, datos, codigo in (
                ('JSON inválido', b'{', 400),
                ('estado de tipo incorrecto', dict(cuerpo, activo='true'), 400),
                ('tipo inexistente', dict(cuerpo, idTipoCaracteristica=falta[1:]), 422),
                ('tipo inactivo', dict(cuerpo, idTipoCaracteristica=inactivo), 422),
                ('nombre excesivo', dict(cuerpo, nombre='x' * 156), 422),
                ('UUID del cliente', dict(cuerpo, idCaracteristica=falta[1:]), 400)):
            self.comprobar('POST ' + nombre, 'POST', '', datos, codigo)
        codigo, dto = self.comprobar('POST característica', 'POST', '', cuerpo, 201)
        if codigo != 201:
            raise RuntimeError('No se creó la característica temporal')
        ident = str(uuid.UUID(dto[self.clave]))
        ruta = '/' + ident
        self.comprobar('GET característica creada', 'GET', ruta, None, 200,
                       lambda v: v['nombre'] == self.marca and v['idTipoCaracteristica'] == tipo)
        producto = self.insertar('producto', nombre=self.marca, activo=True, precio_sugerido=1)
        asociacion = self.insertar('producto_caracteristica', id_producto=producto,
                                  id_caracteristica=ident, valor=self.marca)
        self.comprobar('DELETE con asociación', 'DELETE', ruta, None, 409)
        self.comprobar('PUT desactiva una característica asociada', 'PUT', ruta,
                       dict(cuerpo, activo=False, observaciones=self.marca), 200,
                       lambda v: v['activo'] is False)
        self.resultado('PUT y DELETE rechazado conservan la asociación', self.sql(
            f"SELECT valor FROM producto_caracteristica WHERE id_producto_caracteristica='{asociacion}';") == self.marca)
        self.limpiar_filas(len(self.filas) - 1)
        self.borrar(ident)
        self.resultado('DELETE conserva el tipo', self.sql(
            f"SELECT count(*) FROM tipo_caracteristica WHERE id_tipo_caracteristica='{tipo}';") == '1')

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
                # Las asociaciones SQL temporales son las únicas que pueden impedir
                # borrar una característica creada por este script.
                dependencias = [(t, i) for t, i in self.filas if t == 'producto_caracteristica'
                                and self.recurso == 'caracteristicas']
                for t, i in dependencias:
                    self.sql(f"DELETE FROM {t} WHERE id_{t}='{i}';")
                    self.filas.remove((t, i))
                for ident in list(self.creados):
                    self.borrar(ident)
                if self.filas:
                    self.limpiar_filas()
                if self.baseline is not None:
                    self.resultado('Contenido de las 4 tablas idéntico al inicial', self.baseline == self.huella())
            except Exception as ex:
                self.resultado('Limpieza completa', False, ex)
                print('UUID de registros propios:', self.creados, self.filas)
            print(f'RESUMEN: {self.ok} OK / {self.fallos} FALLIDAS / {self.omitidas} PENDIENTES')
        return int(bool(self.fallos or self.omitidas))


def main(clase=Pruebas):
    parser = argparse.ArgumentParser(description=clase.__doc__ or __doc__)
    parser.add_argument('--raiz', default=DEFAULT_ROOT)
    args = parser.parse_args()
    if urllib.parse.urlparse(args.raiz).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Use la API local de desarrollo')
    return clase(args).ejecutar()


if __name__ == '__main__':
    sys.exit(main())
