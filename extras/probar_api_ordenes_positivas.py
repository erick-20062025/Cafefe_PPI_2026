#!/usr/bin/env python3
"""Pruebas positivas + límites de Orden REST para Cafefe (solo DESARROLLO).

Reutiliza un camarero y productos activos; crea órdenes de prueba, las consulta,
prueba promociones si existen y limpia solo órdenes identificadas por una etiqueta
aleatoria propia. NO crea ni borra empleados/productos/facturas/pagos existentes.

Ejecutar desde cualquier carpeta:
    python3 extras/probar_api_ordenes_positivas.py
Opciones: --base URL, --contenedor NOMBRE, --usuario DB_USER, --bd DB_NAME
"""
import argparse
from collections import Counter
from datetime import datetime
from decimal import Decimal, InvalidOperation
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

DEFAULT_BASE = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/ordenes'


def consultar(metodo, url, cuerpo=None):
    datos = None if cuerpo is None else json.dumps(cuerpo, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=datos, method=metodo,
                                 headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            estado, raw = resp.status, resp.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as exc:
        estado, raw = exc.code, exc.read().decode('utf-8', 'replace')
    try:
        return estado, json.loads(raw) if raw else None
    except json.JSONDecodeError:
        return estado, raw[:400]


class Ejecutor:
    def __init__(self, args):
        self.args = args
        self.base = args.base.rstrip('/')
        self.marca = 'PRUEBA_ORDEN_REST_POS_' + uuid.uuid4().hex.upper()
        self.buenas = self.fallas = self.omitidas = 0
        self.creadas = []

    def db(self, sql):
        cmd = ['docker', 'exec', '-i', self.args.contenedor, 'psql', '-X', '-q', '-t', '-A',
               '-F', '\t', '-v', 'ON_ERROR_STOP=1', '-U', self.args.usuario,
               '-d', self.args.bd]
        p = subprocess.run(cmd, input=sql, capture_output=True, text=True, timeout=25)
        if p.returncode:
            raise RuntimeError('PostgreSQL no ejecutó la consulta: ' + p.stderr.strip()[:550])
        return [line for line in p.stdout.splitlines() if line.strip()]

    def http(self, metodo, ruta='', body=None):
        return consultar(metodo, self.base + ruta, body)

    def verificar(self, nombre, condicion, detalle=''):
        correcto = bool(condicion)
        self.buenas += int(correcto)
        self.fallas += int(not correcto)
        print(('OK     ' if correcto else 'REVISAR') + ' ' + nombre)
        if not correcto and detalle:
            print('        ' + str(detalle)[:500])
        return correcto

    def omitir(self, nombre, motivo):
        self.omitidas += 1
        print('OMITIR  ' + nombre + ' — ' + motivo)

    def estado(self, nombre, metodo, ruta, body, esperado):
        codigo, respuesta = self.http(metodo, ruta, body)
        # Si un POST que debía fallar termina creando algo, guardamos su UUID
        # para intentar limpiarlo; NO lo consideramos aprobado.
        if metodo == 'POST' and ruta in ('', '/publicas') and codigo == 201 and esperado != 201:
            try:
                self.creadas.append(str(uuid.UUID(respuesta['idOrden'])))
            except (KeyError, TypeError, ValueError, AttributeError):
                pass
        envoltura = (codigo < 400 or
                     (isinstance(respuesta, dict) and
                      all(k in respuesta for k in ('codigo', 'mensaje', 'detalle'))))
        self.verificar(nombre, codigo == esperado and envoltura,
                       f'HTTP {codigo}, se esperaba {esperado}; respuesta: {respuesta}')
        return codigo, respuesta

    def previos(self):
        codigo, _ = self.http('GET')
        if codigo != 200:
            raise RuntimeError(f'API GET /ordenes respondió HTTP {codigo}. Verifica Liberty y PostgreSQL.')
        # Datos EXISTENTES, solo lectura. Nunca insertar un empleado/producto ajeno.
        camareros = self.db("""SELECT er.id_empleado_rol::text
          FROM empleado_rol er
          JOIN empleado e ON e.id_empleado = er.id_empleado
          JOIN rol r ON r.id_rol = er.id_rol
          WHERE er.activo IS TRUE AND e.activo IS TRUE AND r.activo IS TRUE
            AND LOWER(BTRIM(r.nombre)) = 'camarero'
          ORDER BY er.id_empleado_rol LIMIT 1;""")
        if not camareros:
            raise RuntimeError('No existe relación EmpleadoRol activa con rol camarero. Créala en la interfaz.')
        productos = self.db("""SELECT p.id_producto::text, p.precio_sugerido::text
          FROM producto p
          WHERE p.activo IS TRUE AND p.precio_sugerido IS NOT NULL
            AND p.precio_sugerido > 0
            AND ROUND(p.precio_sugerido, 2) = p.precio_sugerido
          ORDER BY p.nombre, p.id_producto LIMIT 2;""")
        if not productos:
            raise RuntimeError('No hay producto activo con precio positivo de 2 decimales.')
        self.camarero = str(uuid.UUID(camareros[0]))
        self.productos = [(str(uuid.UUID(line.split('\t')[0])), Decimal(line.split('\t')[1]))
                          for line in productos]
        self.verificar('Precondición: camarero y productos reales', True)
        print('        IDs de prueba:', self.camarero, self.productos[0][0])

    def contar_tablas(self):
        rows = self.db('SELECT (SELECT COUNT(*) FROM orden), (SELECT COUNT(*) FROM orden_producto);')
        return tuple(map(int, rows[0].split('\t')))

    def probar_limites(self):
        prod = self.productos[0][0]
        base = {'idEmpleadoRol': self.camarero,
                'productos': [{'idProducto': prod, 'cantidad': 1}]}
        # 400: error de estructura/tipo; 422: dato presente pero inaceptable.
        casos = [
            ('Interna sin camarero', {k: v for k, v in base.items() if k != 'idEmpleadoRol'}, 422),
            ('Interna camarero null', {**base, 'idEmpleadoRol': None}, 422),
            ('Interna camarero en blanco', {**base, 'idEmpleadoRol': '   '}, 422),
            ('Interna sin productos', {'idEmpleadoRol': self.camarero}, 422),
            ('Interna productos null', {**base, 'productos': None}, 422),
            ('Interna productos lista vacía', {**base, 'productos': []}, 422),
            ('Interna producto sin ID', {**base, 'productos': [{}]}, 422),
            ('Interna observación excesiva', {**base, 'productos': [
                {'idProducto': prod, 'observaciones': 'X'*401}]}, 422),
            ('Interna cantidad null', {**base, 'productos': [
                {'idProducto': prod, 'cantidad': None}]}, 422),
            ('Interna 21 unidades', {**base, 'productos': [
                {'idProducto': prod, 'cantidad': 21}]}, 422),
            ('Interna tipo incorrecto en nombre', {**base, 'nombreCliente': 123}, 400),
            ('Interna fecha vacía explícita', {**base, 'fechaCreacion': '   '}, 422),
        ]
        antes = self.contar_tablas()
        for nombre, contenido, esperado in casos:
            self.estado(nombre, 'POST', '', contenido, esperado)
        publicos = [
            ('Pública sin nombre', {'productos': base['productos']}, 422),
            ('Pública nombre null', {'nombreCliente': None, 'productos': base['productos']}, 422),
            ('Pública nombre en blanco', {'nombreCliente': ' \u200b ', 'productos': base['productos']}, 422),
            ('Pública nombre demasiado largo', {'nombreCliente': 'X'*101,
                                                'productos': base['productos']}, 422),
            ('Pública productos vacíos', {'nombreCliente': 'Cliente Prueba', 'productos': []}, 422),
            ('Pública intenta elegir empleado', {'nombreCliente': 'Cliente Prueba',
                                                 **base}, 400),
        ]
        for nombre, contenido, esperado in publicos:
            self.estado(nombre, 'POST', '/publicas', contenido, esperado)
        # Un producto existente seguido de otro inexistente no debe guardar NADA.
        mezclado = {**base, 'nombreCliente': self.marca,
                    'productos': [{'idProducto': prod}, {'idProducto': str(uuid.uuid4())}]}
        self.estado('Orden con producto válido + inexistente', 'POST', '', mezclado, 422)
        despues = self.contar_tablas()
        self.verificar('Solicitudes rechazadas no alteran número de órdenes ni filas',
                       antes == despues, f'Antes {antes}; después {despues}')

    def comprobar_orden(self, id_orden, esperados, camarero, nombre, fecha=None):
        codigo, det = self.estado(nombre + ': GET por ID', 'GET', '/' + id_orden, None, 200)
        if codigo != 200 or not isinstance(det, dict):
            return
        self.verificar(nombre + ': pertenece al camarero correcto',
                       det.get('idEmpleadoRol') == camarero, det)
        filas = det.get('productos')
        if not isinstance(filas, list):
            self.verificar(nombre + ': detalles son lista JSON', False, det)
            return
        self.verificar(nombre + ': número correcto de unidades',
                       len(filas) == len(esperados), f'{len(filas)} vs {len(esperados)}')
        reales = Counter(str(f.get('idProducto')) for f in filas if isinstance(f, dict))
        deseados = Counter(x[0] for x in esperados)
        self.verificar(nombre + ': IDs de productos exactos', reales == deseados,
                       f'{reales} frente a {deseados}')
        for f in filas:
            try:
                uuid.UUID(f['idOrdenProducto'])
            except (KeyError, TypeError, ValueError, AttributeError):
                self.verificar(nombre + ': UUID de cada fila válido', False, f)
                break
        else:
            self.verificar(nombre + ': UUID de cada fila válido', True)
        self.verificar(nombre + ': observaciones conservan etiqueta de prueba',
                       all(self.marca in (f.get('observaciones') or '') for f in filas
                           if isinstance(f, dict)))
        try:
            precios = [Decimal(str(f['precio'])) for f in filas]
            total = Decimal(str(det['total']))
            calculado = sum((p for _, p in esperados), Decimal('0'))
            ok_precios = sorted(precios) == sorted(p for _, p in esperados)
            ok_total = total == calculado == sum(precios)
        except (KeyError, InvalidOperation, TypeError, ValueError):
            ok_precios, ok_total, total = False, False, '?'
        self.verificar(nombre + ': precios del catálogo, no del navegador', ok_precios,
                       f'Precios: {filas}')
        self.verificar(nombre + ': total guardado coincide con suma', ok_total,
                       f'Total: {total}')
        if fecha:
            try:
                actual = datetime.fromisoformat(det['fechaCreacion'].replace('Z', '+00:00'))
                valido = actual == datetime.fromisoformat(fecha)
            except (KeyError, TypeError, ValueError):
                valido = False
            self.verificar(nombre + ': permite fecha anterior con zona horaria', valido,
                           det.get('fechaCreacion'))

        codigo2, items = self.estado(nombre + ': GET solo productos', 'GET',
                                     '/' + id_orden + '/productos', None, 200)
        if codigo2 == 200:
            self.verificar(nombre + ': endpoint /productos filtra las mismas filas',
                           isinstance(items, list) and
                           {x.get('idOrdenProducto') for x in items if isinstance(x, dict)} ==
                           {x.get('idOrdenProducto') for x in filas if isinstance(x, dict)})

    def crear_interna(self):
        p1, precio1 = self.productos[0]
        p2, precio2 = self.productos[-1]
        fecha = '2026-11-10T09:45:00-06:00'  # Debe permitirse fecha anterior.
        cuerpo = {
            'idEmpleadoRol': self.camarero,
            'fechaCreacion': fecha,
            'nombreCliente': ' Prueba API ' + self.marca + ' ',
            'productos': [
                {'idProducto': p1, 'cantidad': 2, 'observaciones': 'Sin azúcar'},
                {'idProducto': p2, 'cantidad': 1, 'observaciones': '   '},
            ],
        }
        codigo, dato = self.estado('Crear orden interna con 3 unidades', 'POST', '', cuerpo, 201)
        if codigo != 201 or not isinstance(dato, dict):
            return
        try:
            oid = str(uuid.UUID(dato['idOrden']))
        except (KeyError, TypeError, ValueError, AttributeError):
            self.verificar('POST devuelve idOrden UUID', False, dato)
            return
        self.creadas.append(oid)
        self.verificar('POST devuelve idOrden UUID', True)
        self.comprobar_orden(oid, [(p1, precio1), (p1, precio1), (p2, precio2)],
                             self.camarero, 'Orden interna', fecha=fecha)
        codigo, lista = self.estado('Listar órdenes después de crear', 'GET', '', None, 200)
        self.verificar('Orden nueva aparece en listado general',
                       codigo == 200 and isinstance(lista, list) and
                       any(isinstance(x, dict) and x.get('idOrden') == oid for x in lista))
        codigo, lista = self.estado('Listar órdenes pendientes después de crear',
                                   'GET', '/pendientes', None, 200)
        self.verificar('Orden sin factura pagada aparece en pendientes',
                       codigo == 200 and isinstance(lista, list) and
                       any(isinstance(x, dict) and x.get('idOrden') == oid for x in lista))
        self.estado('No se puede borrar orden con productos', 'DELETE', '/' + oid, None, 422)
        codigo, _ = self.estado('Orden sigue existiendo después del DELETE rechazado',
                                'GET', '/' + oid, None, 200)
        if codigo == 200:
            self.probar_promociones(oid)

    def probar_promociones(self, oid):
        codigo, disponibles = self.estado('Listar solo promociones aplicables a la orden',
                                          'GET', '/' + oid + '/promociones', None, 200)
        if codigo != 200 or not isinstance(disponibles, list):
            return
        if not disponibles:
            self.omitir('Aplicar promoción real', 'No hay una promoción aplicable a estos productos y fecha')
            return
        promo = disponibles[0]
        try:
            id_promo = str(uuid.UUID(promo['idDescuento']))
        except (KeyError, TypeError, ValueError):
            self.verificar('ID de promoción ofrecida es UUID', False, promo)
            return
        ruta = '/' + oid + '/promociones/' + id_promo
        def precio_actual():
            codigo, det = self.http('GET', '/' + oid)
            if codigo != 200 or not isinstance(det, dict):
                raise RuntimeError('No se pudo leer detalle al probar promoción')
            return Decimal(str(det['total'])), det['productos']
        previo, _ = precio_actual()
        codigo1, r1 = self.estado('Aplicar promoción vigente', 'POST', ruta, None, 200)
        if codigo1 != 200:
            return
        primero, filas1 = precio_actual()
        self.verificar('Promoción reduce o conserva total, sin aumentarlo',
                       primero <= previo and isinstance(r1, dict) and
                       r1.get('productosAfectados', 0) > 0,
                       f'Antes ${previo}; después ${primero}')
        self.verificar('Promoción conserva 3 productos', len(filas1) == 3)
        codigo2, r2 = self.estado('Reaplicar promoción sobre precio descontado',
                                  'POST', ruta, None, 200)
        if codigo2 == 200:
            segundo, filas2 = precio_actual()
            self.verificar('Segunda promoción no aumenta total', segundo <= primero,
                           f'Antes ${primero}; después ${segundo}')
            self.verificar('Reaplicación conserva 3 productos', len(filas2) == 3)

    def crear_publica(self):
        prod, precio = self.productos[0]
        nombre = 'Cliente ' + self.marca
        cuerpo = {'nombreCliente': nombre,
                  'productos': [{'idProducto': prod, 'cantidad': 1,
                                 'observaciones': 'Pedido web de prueba'}]}
        codigo, respuesta = self.http('POST', '/publicas', cuerpo)
        if codigo == 503:
            self.omitir('Crear pedido público',
                        'Falta configurar CAFEFE_CAMARERO_VIRTUAL_ID en server.env y reiniciar Liberty')
            return
        self.verificar('Crear pedido público con empleado virtual', codigo == 201,
                       f'HTTP {codigo}: {respuesta}')
        if codigo != 201 or not isinstance(respuesta, dict):
            return
        try:
            oid = str(uuid.UUID(respuesta['idOrden']))
        except (KeyError, TypeError, ValueError, AttributeError):
            self.verificar('Pedido público devuelve UUID válido', False, respuesta)
            return
        self.creadas.append(oid)
        self.verificar('Pedido público devuelve UUID válido', True)
        virtual = respuesta.get('idEmpleadoRol')
        self.verificar('Pedido público asigna relación EmpleadoRol',
                       isinstance(virtual, str) and bool(virtual))
        self.comprobar_orden(oid, [(prod, precio)], virtual, 'Orden pública')

    def limpiar(self):
        # Busca solo filas con etiqueta UUID creada por este proceso. Si una orden
        # contiene además otras filas, NO se elimina y exige inspección manual.
        etiqueta = self.marca  # [A-Z0-9_] exclusivamente; no hay inyección SQL.
        if not re.fullmatch(r'[A-Z0-9_]+', etiqueta):
            raise RuntimeError('Etiqueta insegura: no se permite limpieza')
        ordenes = self.db("SELECT DISTINCT op.id_orden::text FROM orden_producto op "
                          f"WHERE op.observaciones LIKE '%{etiqueta}%' ORDER BY 1;")
        ids = sorted(set(str(uuid.UUID(x.strip())) for x in ordenes) | set(self.creadas))
        print('\nLimpieza de órdenes creadas por esta ejecución:', len(ids))
        for oid in ids:
            sql = f"""
BEGIN;
DO $limpiar$
DECLARE v_orden uuid := '{oid}'::uuid;
BEGIN
 IF EXISTS (SELECT 1 FROM orden_producto op
            WHERE op.id_orden = v_orden AND
            (op.observaciones IS NULL OR op.observaciones NOT LIKE '%{etiqueta}%')) THEN
    RAISE EXCEPTION 'La orden contiene filas ajenas: no se eliminará';
 END IF;
 IF EXISTS (SELECT 1 FROM factura_orden_producto f
            JOIN orden_producto op ON op.id_orden_producto = f.id_orden_producto
            WHERE op.id_orden = v_orden) THEN
    RAISE EXCEPTION 'La orden está relacionada con una factura: no se eliminará';
 END IF;
 DELETE FROM orden_producto WHERE id_orden = v_orden;
 DELETE FROM orden WHERE id_orden = v_orden;
END $limpiar$;
COMMIT;
"""
            try:
                self.db(sql)
                self.verificar('Limpiar orden creada ' + oid[:8], True)
            except Exception as exc:
                self.verificar('Limpiar orden creada ' + oid[:8], False, str(exc))
                print('        UUID pendiente de limpieza manual:', oid)
        # Si no hubo filas creadas no intentamos borrar órdenes ajenas.
        faltantes = self.db("SELECT COUNT(*) FROM orden_producto "
                            f"WHERE observaciones LIKE '%{etiqueta}%';")
        self.verificar('No quedan filas de esta prueba', faltantes == ['0'], faltantes)

    def ejecutar(self):
        try:
            print('API:', self.base)
            print('Base de datos:', self.args.bd, '/ contenedor:', self.args.contenedor)
            print('ETIQUETA ÚNICA:', self.marca)
            print('Se usarán solo empleados y productos existentes. NO usar en producción.\n')
            self.previos()
            self.probar_limites()
            self.crear_interna()
            self.crear_publica()
        except Exception as exc:
            self.verificar('Ejecución completa sin errores inesperados', False, str(exc))
        finally:
            try:
                self.limpiar()
            except Exception as exc:
                self.verificar('Limpieza automática', False, str(exc))
                print('ALERTA: revisa en PostgreSQL la etiqueta:', self.marca)
            print(f'\nRESUMEN: {self.buenas} OK / {self.fallas} FALLIDAS / '
                  f'{self.omitidas} OMITIDAS')
            if self.omitidas:
                print('Las pruebas omitidas NO equivalen a pruebas aprobadas.')
        return 1 if self.fallas else 0


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', default=DEFAULT_BASE, help='URL del recurso /api/ordenes')
    p.add_argument('--contenedor', default='postgres-universidad')
    p.add_argument('--usuario', default='erick', help='Usuario PostgreSQL')
    p.add_argument('--bd', default='programacion_internet', help='Base de datos PostgreSQL')
    args = p.parse_args()
    return Ejecutor(args).ejecutar()


if __name__ == '__main__':
    sys.exit(main())
