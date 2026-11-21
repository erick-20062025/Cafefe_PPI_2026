#!/usr/bin/env python3
"""Prueba HTTP/JPA contra un PostgreSQL desechable con los tres SQL oficiales.

Requiere el WAR construido (mvn package), Liberty instalado en target/liberty,
PostgreSQL 16 local y los archivos oficiales en Downloads (o --fuentes).
No utiliza server.xml local, credenciales ni conexiones de la base habitual.
No aplica db/migrations. Todos los datos de prueba se eliminan al terminar.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen, Request, build_opener, HTTPCookieProcessor
from http.cookiejar import CookieJar
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout


def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fuentes', type=Path, default=Path.home() / 'Downloads')
    parser.add_argument('--pg-bin', type=Path, default=Path('/usr/lib/postgresql/16/bin'))
    parser.add_argument('--browser', action='store_true', help='Servir el frontend en un origen temporal separado hasta crear STOP')
    args = parser.parse_args()
    sources = [args.fuentes / name for name in (
        'Pasted markdown(1).md', 'producto_preciosugerido_20260922.sql',
        'producto_tipo_producto_20260918(1).sql')]
    scripts = ['\n'.join(line for line in p.read_text().splitlines()
                         if not line.strip().startswith('```')) for p in sources]
    war = ROOT / 'target/Cafefe_PPI_2026-1.0-SNAPSHOT.war'
    wlp = ROOT / 'target/liberty/wlp'
    jdbc = next((wlp / 'usr/servers/defaultServer/lib/postgres').glob('postgresql-*.jar'))
    assert war.is_file(), 'Ejecutá mvn package primero'
    with tempfile.TemporaryDirectory(prefix='cafefe-oficial-') as tmp:
        tmp = Path(tmp)
        pgdata = tmp / 'pgdata'
        pgport, httpport = port(), port()
        frontport = port() if args.browser else 5501
        origen = f'http://127.0.0.1:{frontport}'
        server = tmp / 'liberty/servers/oficial'
        (server / 'apps').mkdir(parents=True)
        shutil.copy2(war, server / 'apps/cafefe.war')
        shutil.copy2(jdbc, server / jdbc.name)
        # Probar el pool real del repositorio, sustituyendo solo puertos y rutas del servidor temporal.
        config = ET.parse(ROOT / 'src/main/liberty/config/server.xml')
        root = config.getroot()
        endpoint = root.find('httpEndpoint')
        endpoint.set('host', '127.0.0.1')
        endpoint.set('httpPort', str(httpport))
        endpoint.set('httpsPort', '-1')
        for files in root.findall('.//library/fileset'):
            files.set('dir', '${server.config.dir}')
            files.set('includes', jdbc.name)
        root.find('cors').set('domain', '/cafefe/api/')
        root.find('cors').set('allowedOrigins', origen)
        (server / 'jvm.options').write_text(f'-Dcafefe.frontend.origin={origen}\n')
        ET.SubElement(root, 'webApplication', {'location': 'cafefe.war', 'contextRoot': '/cafefe'})
        config.write(server / 'server.xml', encoding='unicode')
        env = dict(os.environ, WLP_USER_DIR=str(tmp / 'liberty'), PG_HOST='127.0.0.1',
                   PG_PORT=str(pgport), PG_DB='postgres', PG_USER='prueba_oficial', PG_PASS='')
        # El cluster privado no comparte directorio ni puerto con PostgreSQL habitual.
        run([args.pg_bin / 'initdb', '-D', pgdata, '-U', 'prueba_oficial', '-A', 'trust', '--no-locale', '--encoding=UTF8'])
        started = False
        liberty = None
        try:
            run([args.pg_bin / 'pg_ctl', '-D', pgdata, '-l', tmp / 'postgres.log',
                 '-o', f'-h 127.0.0.1 -p {pgport} -k {tmp}', '-w', 'start'])
            started = True

            def sql(statement):
                return run([args.pg_bin / 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1',
                            '-p', pgport, '-U', 'prueba_oficial', '-d', 'postgres', '-At'], input=statement)

            for script in scripts:
                sql(script)
            assert sql("SELECT count(*) FROM information_schema.tables WHERE table_schema='public';").strip() == '19'
            assert sql("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('precio_base','descuento_aplicado','promocion_aplicada','caracteristicas_historicas','producto_historico','estado_preparacion','cliente_web','atendido_por','cancelada_por','cancelada_en','efectivo_recibido','cambio_entregado');").strip() == '0'
            consulta_esquema = "SELECT table_name,column_name,data_type,is_nullable,COALESCE(column_default,'') FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position;"
            esquema_antes = sql(consulta_esquema)
            factura, otra, producto, orden, detalle = [str(uuid.uuid4()) for _ in range(5)]
            sql(f"""INSERT INTO factura(id_factura,estado) VALUES ('{factura}','PENDIENTE'),('{otra}','PAGADA');
INSERT INTO producto(id_producto,nombre,precio_sugerido,activo) VALUES ('{producto}','Café de prueba',99.99,true);
INSERT INTO orden(id_orden) VALUES ('{orden}');
INSERT INTO orden_producto(id_orden_producto,id_orden,id_producto,precio) VALUES ('{detalle}','{orden}','{producto}',12.50);
INSERT INTO factura_orden_producto(id_factura_orden_producto,id_factura,id_orden_producto,precio)
VALUES ('{uuid.uuid4()}','{factura}','{detalle}',7.25), ('{uuid.uuid4()}','{otra}','{detalle}',2.00);""")
            empleado, rol, asignacion = [str(uuid.uuid4()) for _ in range(3)]
            sql(f"""INSERT INTO empleado(id_empleado,nombre,activo) VALUES ('{empleado}','Caja prueba',true);
INSERT INTO rol(id_rol,nombre,activo) VALUES ('{rol}','cajero',true);
INSERT INTO empleado_rol(id_empleado_rol,id_empleado,id_rol,activo) VALUES ('{asignacion}','{empleado}','{rol}',true);""")
            personal = {}
            for nombre_rol in ('camarero', 'barista', 'cocinero'):
                eid, rid, aid = [str(uuid.uuid4()) for _ in range(3)]
                personal[nombre_rol] = eid
                sql(f"""INSERT INTO empleado(id_empleado,nombre,activo) VALUES ('{eid}','{nombre_rol}',true);
INSERT INTO rol(id_rol,nombre,activo) VALUES ('{rid}','{nombre_rol}',true);
INSERT INTO empleado_rol(id_empleado_rol,id_empleado,id_rol,activo) VALUES ('{aid}','{eid}','{rid}',true);""")
                if nombre_rol == 'camarero':
                    env['CAFEFE_CAMARERO_VIRTUAL_ID'] = aid
            caja = str(uuid.uuid4())
            sql(f"INSERT INTO caja(id_caja,nombre,activo) VALUES ('{caja}','Caja de prueba',true);")
            output = (tmp / 'liberty.log').open('w')
            liberty = subprocess.Popen([str(wlp / 'bin/server'), 'run', 'oficial'],
                                       env=env, stdout=output, stderr=subprocess.STDOUT)
            base = f'http://127.0.0.1:{httpport}/cafefe/api/pagos/facturas/'

            def get(path):
                try:
                    with urlopen(base + path, timeout=10) as response:
                        return response.status, json.load(response)
                except HTTPError as error:
                    return error.code, json.load(error)

            deadline = time.monotonic() + 120
            while True:
                try:
                    status, body = get(factura + '/productos')
                    if status != 404:
                        break
                except (URLError, TimeoutError, json.JSONDecodeError):
                    pass
                if time.monotonic() >= deadline or liberty.poll() is not None:
                    raise AssertionError('Liberty no inició: ' + (tmp / 'liberty.log').read_text()[-5000:])
                time.sleep(1)
            assert status == 200, (status, body)
            assert body == [{'idOrdenProducto': detalle, 'idProducto': producto,
                             'nombreProducto': 'Café de prueba', 'precio': 7.25}], body
            print('PASS: GET real con JPA; precio facturado 7.25, distinto de orden 12.50 y catálogo 99.99', flush=True)
            status, body = get(otra + '/productos')
            assert status == 200 and len(body) == 1 and body[0]['precio'] == 2.0, body
            print('PASS: aislamiento entre facturas y lectura de factura pagada', flush=True)
            assert get('invalido/productos')[0] == 400
            assert get(str(uuid.uuid4()) + '/productos')[0] == 404
            print('PASS: UUID inválido 400 y factura inexistente 404', flush=True)
            api = f'http://127.0.0.1:{httpport}/cafefe/api/'
            cliente = build_opener(HTTPCookieProcessor(CookieJar()))

            def request(path, data=None, opener=cliente):
                req = Request(api + path, data=None if data is None else json.dumps(data).encode(),
                              headers={'Content-Type': 'application/json', 'Origin': origen})
                try:
                    with opener.open(req, timeout=10) as response:
                        return response.status, json.load(response)
                except HTTPError as error:
                    return error.code, json.load(error)

            abonos = f'cobros/facturas/{factura}/abonos'
            assert request(abonos)[0] == 401
            status, body = request('sesion', {'nombre': 'Caja prueba', 'uuid': empleado})
            assert status == 200, (status, body)
            assert request(abonos) == (200, [])
            for data, expected in [
                ({'monto': 0, 'tipoPago': 'EFECTIVO'}, 422),
                ({'monto': 2, 'tipoPago': 'EFECTIVO', 'efectivoRecibido': 1}, 422),
                ({'monto': 8, 'tipoPago': 'EFECTIVO'}, 422),
                ({'monto': 2, 'tipoPago': 'TARJETA'}, 422),
            ]:
                status, body = request(abonos, data)
                assert status == expected, (data, status, body)
            assert request(abonos) == (200, [])
            status, body = request(abonos, {'monto': 2, 'tipoPago': 'TARJETA', 'referenciaExterna': 'TEST-001'})
            assert status == 201 and body['saldo'] == 5.25 and not body['completado'], (status, body)
            assert request(abonos, {'monto': 1, 'tipoPago': 'TARJETA', 'referenciaExterna': 'TEST-001'})[0] == 409
            status, body = request(abonos, {'monto': 5.25, 'tipoPago': 'EFECTIVO', 'efectivoRecibido': 10})
            assert status == 201 and body['saldo'] == 0 and body['completado'], (status, body)
            # Nueva petición recupera únicamente lo persistido; no se inventa historial del cambio.
            status, body = request(abonos)
            assert status == 200 and len(body) == 2, (status, body)
            assert sorted(x['monto'] for x in body) == [2, 5.25], body
            assert all('efectivoRecibido' not in x and 'cambioEntregado' not in x for x in body), body
            assert sql(f"SELECT estado FROM factura WHERE id_factura='{factura}';").strip() == 'PAGADA'
            assert sql(f"SELECT SUM(pd.monto) FROM pago_detalle pd JOIN pago p USING(id_pago) WHERE p.id_factura='{factura}';").strip() == '7.25'
            assert request(abonos, {'monto': 1, 'tipoPago': 'EFECTIVO'})[0] == 409
            print('PASS: sesión cajero, acceso anónimo rechazado y lista vacía', flush=True)
            print('PASS: abonos inválidos rechazados sin escrituras; referencia duplicada rechazada', flush=True)
            print('PASS: abono parcial, efectivo con cambio, factura pagada y recuperación de abonos oficiales', flush=True)
            anonimo = build_opener(HTTPCookieProcessor(CookieJar()))
            pedido = {'nombreCliente': 'Ana prueba', 'productos': [
                {'idProducto': producto, 'cantidad': 2, 'observaciones': 'Sin azúcar'}]}
            status, nueva = request('ordenes/publicas', pedido, anonimo)
            assert status == 201, (status, nueva)
            oid = nueva['idOrden']
            assert nueva['total'] == 199.98, nueva
            assert request('ordenes/publicas', {'nombreCliente': '', 'productos': []}, anonimo)[0] in (400, 422)
            assert request('ordenes/pendientes', opener=anonimo)[0] == 401
            assert request('ordenes/pendientes')[0] == 403
            lectores = []
            for rol_recepcion in ('barista', 'cocinero'):
                lector = build_opener(HTTPCookieProcessor(CookieJar()))
                assert request('sesion', {'nombre': rol_recepcion, 'uuid': personal[rol_recepcion]}, lector)[0] == 200
                lectores.append(lector)
                status, pendientes = request('ordenes/pendientes', opener=lector)
                assert status == 200 and oid in [o['idOrden'] for o in pendientes], (status, pendientes)
                cabecera = next(o for o in pendientes if o['idOrden'] == oid)
                assert cabecera['cliente'] == 'Ana prueba' and cabecera['estadoPago'] == 'PENDIENTE_DE_PAGO', cabecera
                status, detalles = request(f'ordenes/{oid}/productos', opener=lector)
                assert status == 200 and len(detalles) == 2, (status, detalles)
                assert all('Cliente: Ana prueba' in d['observaciones'] and 'Sin azúcar' in d['observaciones'] for d in detalles)
                assert request('cobros/facturas/pendientes', opener=lector)[0] == 403
            status, pendientes = request('cobros/ordenes-pendientes')
            assert status == 200 and oid in [o['idOrden'] for o in pendientes]
            status, emitida = request(f'cobros/ordenes/{oid}/facturas', {'idCaja': caja, 'cliente': 'Ana prueba'})
            assert status == 201, (status, emitida)
            fid = emitida['idFactura']
            assert request(f'cobros/ordenes/{oid}/facturas', {'idCaja': caja, 'cliente': 'Ana prueba'})[0] == 409
            status, parcial = request(f'cobros/facturas/{fid}/abonos', {'monto': 50, 'tipoPago': 'EFECTIVO'})
            assert status == 201 and parcial['saldo'] == 149.98, (status, parcial)
            for lector in lectores:
                parcial_recepcion = next(o for o in request('ordenes/pendientes', opener=lector)[1] if o['idOrden'] == oid)
                assert parcial_recepcion['estadoPago'] == 'ABONADA_PARCIALMENTE', parcial_recepcion
            # Cambiar SOLO el catálogo de pruebas no debe cambiar el importe ya registrado.
            sql(f"UPDATE producto SET precio_sugerido=1 WHERE id_producto='{producto}';")
            status, final = request(f'cobros/facturas/{fid}/abonos', {'monto': 149.98, 'tipoPago': 'EFECTIVO', 'efectivoRecibido': 150})
            assert status == 201 and final['total'] == 199.98 and final['completado'], (status, final)
            for lector in lectores:
                assert oid not in [o['idOrden'] for o in request('ordenes/pendientes', opener=lector)[1]]
            status, ticket = request(f'cobros/facturas/{fid}')
            assert status == 200 and ticket['estado'] == 'PAGADA', (status, ticket)
            assert all(d['atendidoPor'] == 'Pedido web' for d in ticket['productos']), ticket
            assert len(ticket['productos']) == 2 and all(d['precio'] == 99.99 for d in ticket['productos']), ticket
            assert sql("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('precio_base','estado_preparacion','cliente_web','cambio_entregado');").strip() == '0'
            print('PASS HU2/HU3/HU4: pedido anónimo → Recepción ambos roles → factura sin preparación → abonos → ticket pagado', flush=True)
            print('PASS: nombre y observaciones conservados, orden pagada desaparece, precios históricos intactos', flush=True)
            assert sql(consulta_esquema) == esquema_antes, 'JPA alteró el esquema'
            print('PASS: esquema oficial de 19 tablas sin columnas añadidas; JPA no alteró su estructura', flush=True)
            if args.browser:
                categoria = str(uuid.uuid4())
                sql(f"""INSERT INTO tipo_producto(id_tipo_producto,nombre,activo) VALUES ('{categoria}','Café prueba',true);
INSERT INTO producto_tipo_producto(id_producto_tipo_producto,id_producto,id_tipo_producto) VALUES ('{uuid.uuid4()}','{producto}','{categoria}');
UPDATE producto SET precio_sugerido=3 WHERE id_producto='{producto}';""")
                front = tmp / 'frontend'
                front.mkdir()
                original = ROOT.parent / 'Cafefe_Fronten_2026'
                for name in ('index.html', 'manifest.webmanifest', 'sw.js'):
                    shutil.copy2(original / name, front / name)
                shutil.copytree(original / 'src', front / 'src')
                (front / 'src/config/api.js').write_text(f"export const API_URL = '{api}';\n")
                http = ThreadingHTTPServer(('127.0.0.1', frontport), partial(SimpleHTTPRequestHandler, directory=str(front)))
                threading.Thread(target=http.serve_forever, daemon=True).start()
                print(json.dumps({'frontend':origen, 'stop':str(tmp/'STOP'), 'cajero':empleado, 'barista':personal['barista'], 'cocinero':personal['cocinero']}, ensure_ascii=False), flush=True)
                try:
                    while not (tmp/'STOP').exists(): time.sleep(1)
                finally:
                    http.shutdown()
                    http.server_close()
        finally:
            if liberty is not None:
                run([wlp / 'bin/server', 'stop', 'oficial'], env=env)
                liberty.wait(timeout=30)
                output.close()
            if started:
                run([args.pg_bin / 'pg_ctl', '-D', pgdata, '-m', 'fast', '-w', 'stop'])


if __name__ == '__main__':
    main()
