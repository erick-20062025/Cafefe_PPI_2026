#!/usr/bin/env python3
"""Pruebas HTTP REST de DescuentoProducto, SOLO para la base de DESARROLLO.

Prueba entradas inválidas y un ciclo positivo con un descuento temporal propio.
No cambia ningún producto ni tipo de descuento existente. Al finalizar, intenta
eliminar primero su asociación temporal y después su descuento temporal.
"""
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

DEFAULT_ROOT = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'
ZONA = ZoneInfo('America/El_Salvador')


class Pruebas:
    def __init__(self, argumentos):
        self.args = argumentos
        self.root = argumentos.raiz.rstrip('/')
        self.base = self.root + '/descuento-productos'
        self.marca = 'PRUEBA_REST_DESC_PROD_' + uuid.uuid4().hex[:16].upper()
        self.ok = self.fallos = self.omitidas = 0
        self.descuento = None
        self.relacion = None
        self.producto = None
        self.tipo = None
        self.maximo = None
        hoy = datetime.now(ZONA).date()
        self.desde = (hoy + timedelta(days=3)).isoformat()
        self.hasta = (hoy + timedelta(days=10)).isoformat()
        self.ayer = (hoy - timedelta(days=1)).isoformat()
        self.fake = str(uuid.uuid4())

    def pedir(self, metodo, ruta='', cuerpo=None, raiz=None):
        url = (raiz or self.base) + ruta
        datos = None if cuerpo is None else (
            cuerpo if isinstance(cuerpo, bytes) else
            json.dumps(cuerpo, ensure_ascii=False).encode('utf-8'))
        solicitud = urllib.request.Request(url, method=metodo, data=datos,
            headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
        try:
            with urllib.request.urlopen(solicitud, timeout=15) as resultado:
                estado = resultado.status
                texto = resultado.read().decode('utf-8', 'replace')
        except urllib.error.HTTPError as error:
            estado = error.code
            texto = error.read().decode('utf-8', 'replace')
        except (urllib.error.URLError, TimeoutError) as error:
            return 0, {'conexion': str(error)}
        try:
            return estado, json.loads(texto) if texto else None
        except ValueError:
            return estado, texto[:300]

    def resultado(self, nombre, correcto, detalle=''):
        if correcto:
            self.ok += 1
            print('OK      ' + nombre)
        else:
            self.fallos += 1
            print('REVISAR ' + nombre + (': ' + str(detalle)[:300] if detalle else ''))

    def comprobar(self, nombre, metodo, ruta, cuerpo, esperado, condicion=None, raiz=None):
        estado, dato = self.pedir(metodo, ruta, cuerpo, raiz)
        mensaje = (esperado < 400 or
                   (isinstance(dato, dict) and dato.get('codigo') == 'API-' + str(esperado)
                    and isinstance(dato.get('detalle'), str) and bool(dato.get('mensaje'))))
        correcto = estado == esperado and mensaje and (condicion is None or condicion(dato))
        self.resultado(nombre, correcto, f'HTTP {estado}; esperado {esperado}; {dato}')
        return estado, dato

    def omitir(self, mensaje):
        self.omitidas += 1
        print('OMITIR  ' + mensaje)

    def sql(self, sql):
        resultado = subprocess.run(['docker', 'exec', '-i', self.args.contenedor,
            'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1',
            '-U', self.args.usuario, '-d', self.args.bd], input=sql,
            capture_output=True, text=True, timeout=20)
        if resultado.returncode:
            raise RuntimeError('No se pudo consultar PostgreSQL: ' + resultado.stderr.strip()[:240])
        return [linea.strip() for linea in resultado.stdout.splitlines() if linea.strip()]

    def cuerpo(self, **cambios):
        dato = {'idDescuento': self.descuento or self.fake,
                'idProducto': self.producto or self.fake,
                'valor': self.maximo if self.maximo and self.maximo < 10 else 10,
                'fechaDesde': self.desde, 'fechaHasta': self.hasta,
                'observaciones': self.marca}
        dato.update(cambios)
        return dato

    def negativas(self):
        for nombre, verbo, ruta, estado in [
            ('GET listado general', 'GET', '', 200),
            ('GET UUID incorrecto', 'GET', '/no-es-uuid', 400),
            ('GET asociación inexistente', 'GET', '/' + self.fake, 404),
            ('GET por descuento UUID incorrecto', 'GET', '/descuentos/invalido', 400),
            ('GET por producto UUID incorrecto', 'GET', '/productos/invalido', 400),
            ('DELETE UUID incorrecto', 'DELETE', '/invalido', 400),
            ('DELETE asociación inexistente', 'DELETE', '/' + self.fake, 404),
            ('GET asociaciones de un descuento sin relaciones', 'GET', '/descuentos/' + self.fake, 200),
            ('GET descuentos de un producto sin relaciones', 'GET', '/productos/' + self.fake, 200),
        ]:
            self.comprobar(nombre, verbo, ruta, None, estado,
                          (lambda v: isinstance(v, list)) if estado == 200 else None)

        errores = [
            ('POST sin cuerpo', b'', 400),
            ('POST JSON malformado', b'{', 400),
            ('POST arreglo JSON', b'[]', 400),
            ('POST objeto vacío', {}, 422),
            ('POST sin descuento', {k:v for k,v in self.cuerpo().items() if k != 'idDescuento'}, 422),
            ('POST descuento null', self.cuerpo(idDescuento=None), 422),
            ('POST descuento vacío', self.cuerpo(idDescuento='   '), 422),
            ('POST descuento número', self.cuerpo(idDescuento=3), 400),
            ('POST descuento UUID incorrecto', self.cuerpo(idDescuento='otro'), 400),
            ('POST sin producto', {k:v for k,v in self.cuerpo().items() if k != 'idProducto'}, 422),
            ('POST producto null', self.cuerpo(idProducto=None), 422),
            ('POST producto vacío', self.cuerpo(idProducto=' '), 422),
            ('POST producto booleano', self.cuerpo(idProducto=False), 400),
            ('POST producto UUID incorrecto', self.cuerpo(idProducto='otro'), 400),
            ('POST sin porcentaje', {k:v for k,v in self.cuerpo().items() if k != 'valor'}, 422),
            ('POST porcentaje null', self.cuerpo(valor=None), 422),
            ('POST porcentaje cero', self.cuerpo(valor=0), 422),
            ('POST porcentaje negativo', self.cuerpo(valor=-3), 422),
            ('POST porcentaje mayor de 100', self.cuerpo(valor=101), 422),
            ('POST porcentaje decimal', self.cuerpo(valor=5.5), 400),
            ('POST porcentaje texto', self.cuerpo(valor='10'), 400),
            ('POST sin fecha desde', {k:v for k,v in self.cuerpo().items() if k != 'fechaDesde'}, 422),
            ('POST fecha desde null', self.cuerpo(fechaDesde=None), 422),
            ('POST fecha desde en blanco', self.cuerpo(fechaDesde='   '), 422),
            ('POST fecha desde número', self.cuerpo(fechaDesde=20261118), 400),
            ('POST fecha desde mal formada', self.cuerpo(fechaDesde='2026-19-01'), 400),
            ('POST fecha completa sin zona', self.cuerpo(fechaDesde='2026-11-18T10:00:00'), 400),
            ('POST fecha hasta anterior', self.cuerpo(fechaHasta=self.ayer), 422),
            ('POST fecha hasta incorrecta', self.cuerpo(fechaHasta='invalid'), 400),
            ('POST fecha hasta número', self.cuerpo(fechaHasta=2), 400),
            ('POST observaciones numéricas', self.cuerpo(observaciones=123), 400),
            ('POST observaciones excesivas', self.cuerpo(observaciones='x'*1001), 422),
            ('POST observaciones con control', self.cuerpo(observaciones='a\x00b'), 422),
            ('POST cliente intenta elegir ID', dict(self.cuerpo(), idDescuentoProducto=self.fake), 400),
            ('POST cliente envía relaciones', dict(self.cuerpo(), descuentoProductoCollection=[]), 400),
            ('POST referencias inexistentes', self.cuerpo(), 422),
        ]
        antes_estado, antes = self.pedir('GET')
        for nombre, cuerpo, estado in errores:
            self.comprobar(nombre, 'POST', '', cuerpo, estado)
        despues_estado, despues = self.pedir('GET')
        self.resultado('POST inválidos no crearon asociaciones',
            antes_estado == despues_estado == 200 and isinstance(antes, list)
            and isinstance(despues, list) and len(antes) == len(despues),
            'Cantidad de asociaciones cambió después de rechazos')

    def positivas(self):
        estado_tipos, tipos = self.pedir('GET', '', raiz=self.root + '/tipo-descuento')
        if estado_tipos != 200 or not isinstance(tipos, list):
            self.omitir('Crear asociación — No se pudo consultar TipoDescuento')
            return
        opciones = [tipo for tipo in tipos if isinstance(tipo, dict)
            and tipo.get('activo') is True and isinstance(tipo.get('descuentoMaximo'), int)
            and not isinstance(tipo.get('descuentoMaximo'), bool)
            and 1 <= tipo['descuentoMaximo'] <= 100
            and isinstance(tipo.get('idTipoDescuento'), str)]
        if not opciones:
            self.omitir('Crear asociación — No hay TipoDescuento activo con máximo entre 1 y 100')
            return
        try:
            productos = self.sql("SELECT id_producto::text FROM producto WHERE activo IS TRUE ORDER BY id_producto LIMIT 1;")
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as ex:
            self.omitir('Crear asociación — No se pudo verificar un producto activo: ' + str(ex)[:180])
            return
        if not productos:
            self.omitir('Crear asociación — No hay productos activos en la base de desarrollo')
            return
        self.tipo = opciones[0]['idTipoDescuento']
        self.maximo = opciones[0]['descuentoMaximo']
        self.producto = productos[0]
        cuerpo_descuento = {
            'idTipoDescuento': self.tipo, 'nombre': self.marca,
            'fechaDesde': self.desde, 'fechaHasta': self.hasta,
            'observaciones': self.marca,
        }
        estado, dato = self.comprobar('Crear descuento temporal auxiliar', 'POST', '',
            cuerpo_descuento, 201,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca
                      and isinstance(v.get('idDescuento'), str),
            raiz=self.root + '/descuentos')
        if estado == 201 and isinstance(dato, dict):
            self.descuento = dato.get('idDescuento')
        if not self.descuento:
            self.omitir('Resto de pruebas positivas — No se creó descuento auxiliar')
            return
        cuerpo = self.cuerpo()
        estado, dato = self.comprobar('POST crear asociación temporal', 'POST', '', cuerpo, 201,
            lambda v: isinstance(v, dict) and v.get('idDescuento') == self.descuento
                       and v.get('idProducto') == self.producto
                       and v.get('valor') == cuerpo['valor']
                       and isinstance(v.get('idDescuentoProducto'), str))
        if estado == 201 and isinstance(dato, dict):
            self.relacion = dato.get('idDescuentoProducto')
        if not self.relacion:
            self.omitir('Resto de pruebas positivas — POST asociación no devolvió ID')
            return
        try:
            uuid.UUID(self.relacion)
        except (ValueError, TypeError):
            self.resultado('UUID de asociación creada es válido', False, self.relacion)
            return
        self.resultado('UUID de asociación creada es válido', True)
        ruta = '/' + self.relacion
        self.comprobar('GET asociación creada', 'GET', ruta, None, 200,
            lambda v: isinstance(v, dict) and v.get('idDescuentoProducto') == self.relacion)
        self.comprobar('GET por descuento contiene solo asociaciones de ese descuento',
            'GET', '/descuentos/' + self.descuento, None, 200,
            lambda v: isinstance(v, list) and any(isinstance(x, dict) and x.get('idDescuentoProducto') == self.relacion for x in v)
                      and all(isinstance(x, dict) and x.get('idDescuento') == self.descuento for x in v))
        self.comprobar('GET por producto contiene solo sus descuentos',
            'GET', '/productos/' + self.producto, None, 200,
            lambda v: isinstance(v, list) and any(isinstance(x, dict) and x.get('idDescuentoProducto') == self.relacion for x in v)
                      and all(isinstance(x, dict) and x.get('idProducto') == self.producto for x in v))
        self.comprobar('GET conserva porcentaje, fechas y observaciones', 'GET', ruta, None, 200,
            lambda v: isinstance(v, dict) and v.get('valor') == cuerpo['valor']
                      and v.get('fechaDesde', '').startswith(self.desde)
                      and v.get('fechaHasta', '').startswith(self.hasta)
                      and v.get('observaciones') == self.marca)
        self.comprobar('POST misma combinación no se duplica', 'POST', '', cuerpo, 409)
        self.comprobar('POST producto inexistente con descuento válido', 'POST', '',
                      self.cuerpo(idProducto=self.fake), 422)
        self.comprobar('POST descuento inexistente con producto válido', 'POST', '',
                      self.cuerpo(idDescuento=self.fake), 422)
        self.comprobar('POST porcentaje cero no crea segunda asociación', 'POST', '',
                      self.cuerpo(valor=0), 422)
        if self.maximo < 100:
            self.comprobar('POST porcentaje mayor al máximo del tipo', 'POST', '',
                          self.cuerpo(valor=self.maximo + 1), 422)
        else:
            self.omitir('POST mayor al máximo del tipo — El máximo del tipo ya es 100')
        self.comprobar('POST fecha de asociación anterior al descuento', 'POST', '',
                      self.cuerpo(fechaDesde=self.ayer, fechaHasta=self.hasta), 422)
        self.comprobar('POST fecha posterior al fin del descuento', 'POST', '',
                      self.cuerpo(fechaDesde=self.desde,
                                 fechaHasta=(datetime.fromisoformat(self.hasta).date() + timedelta(days=1)).isoformat()), 422)
        self.comprobar('DELETE descuento principal con relación devuelve conflicto', 'DELETE',
            '/' + self.descuento, None, 409, raiz=self.root + '/descuentos')
        self.comprobar('GET descuento original sigue existiendo', 'GET',
            '/' + self.descuento, None, 200,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca,
            raiz=self.root + '/descuentos')
        self.comprobar('GET sigue existiendo solo una asociación temporal', 'GET',
            '/descuentos/' + self.descuento, None, 200,
            lambda v: isinstance(v, list) and sum(1 for x in v if isinstance(x, dict)
            and x.get('observaciones') == self.marca) == 1)

    def limpieza(self):
        # Si POST guardó algo pero no devolvió el UUID esperado, recuperar solo
        # asociaciones que referencien NUESTRO descuento y NUESTRA etiqueta.
        ids = [self.relacion] if self.relacion else []
        if self.descuento:
            estado, lista = self.pedir('GET', '/descuentos/' + self.descuento)
            if estado == 200 and isinstance(lista, list):
                ids += [x.get('idDescuentoProducto') for x in lista if isinstance(x, dict)
                        and x.get('idDescuento') == self.descuento
                        and x.get('idProducto') == self.producto
                        and x.get('observaciones') == self.marca
                        and isinstance(x.get('idDescuentoProducto'), str)]
        for id_asociacion in set(x for x in ids if x):
            estado, dato = self.pedir('GET', '/' + id_asociacion)
            if (estado != 200 or not isinstance(dato, dict)
                    or dato.get('idDescuento') != self.descuento
                    or dato.get('idProducto') != self.producto
                    or dato.get('observaciones') != self.marca):
                self.resultado('Limpieza: verificación de pertenencia de la asociación', False,
                               f'REVISIÓN MANUAL: {id_asociacion} {dato}')
                continue
            self.comprobar('Limpieza: eliminar solo asociación temporal',
                          'DELETE', '/' + id_asociacion, None, 200)
            self.comprobar('Limpieza: GET asociación eliminada',
                          'GET', '/' + id_asociacion, None, 404)
        if self.descuento:
            estado, dato = self.pedir('GET', '/' + self.descuento,
                                      raiz=self.root + '/descuentos')
            if (estado != 200 or not isinstance(dato, dict)
                    or dato.get('nombre') != self.marca
                    or dato.get('observaciones') != self.marca):
                self.resultado('Limpieza: confirmar propiedad del descuento', False,
                               f'REVISIÓN MANUAL: {self.descuento} {dato}')
                return
            self.comprobar('Limpieza: eliminar descuento auxiliar', 'DELETE',
                '/' + self.descuento, None, 200, raiz=self.root + '/descuentos')
            self.comprobar('Limpieza: GET descuento auxiliar eliminado', 'GET',
                '/' + self.descuento, None, 404, raiz=self.root + '/descuentos')

    def ejecutar(self):
        print('API:', self.base)
        print('Etiqueta:', self.marca)
        print('SOLO DESARROLLO. Limpieza restringida a registros de esta ejecución.')
        try:
            estado, dato = self.pedir('GET')
            if estado != 200:
                self.resultado('API accesible y PostgreSQL en funcionamiento', False,
                               f'HTTP {estado}: {dato}')
            else:
                self.negativas()
                self.positivas()
        except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired) as ex:
            self.resultado('Ejecución', False, str(ex))
        finally:
            self.limpieza()
            print(f'\nRESUMEN: {self.ok} OK / {self.fallos} FALLIDAS / {self.omitidas} OMITIDAS')
            print('Las pruebas omitidas NO equivalen a pruebas aprobadas.')
        return 0 if self.fallos == 0 else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raiz', default=DEFAULT_ROOT)
    parser.add_argument('--contenedor', default='postgres-universidad')
    parser.add_argument('--usuario', default='erick')
    parser.add_argument('--bd', default='programacion_internet')
    args = parser.parse_args()
    return Pruebas(args).ejecutar()


if __name__ == '__main__':
    sys.exit(main())
