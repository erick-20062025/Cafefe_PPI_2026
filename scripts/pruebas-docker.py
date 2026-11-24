#!/usr/bin/env python3
"""Administra exclusivamente los tres contenedores desechables del tercer parcial.
No inicia recursos al construir imágenes ni publica imágenes.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import uuid
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
LABEL = 'cafefe.ppi115.prueba'
VALUE = 'tercer-parcial'
NOMBRES = ('cafefe.frontend', 'cafefe.backend', 'cafefe.db.pruebas')


def docker(*args, capturar=False):
    return subprocess.run(['docker', *map(str, args)], check=True, text=True,
                          stdout=subprocess.PIPE if capturar else None).stdout


def existe(tipo, nombre):
    return subprocess.run(['docker', tipo, 'inspect', nombre], stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL).returncode == 0


def propio(nombre):
    if not existe('container', nombre):
        return False
    data = json.loads(docker('container', 'inspect', nombre, capturar=True))[0]
    if data.get('Config', {}).get('Labels', {}).get(LABEL) != VALUE:
        raise SystemExit(f'No se tocará el contenedor ajeno: {nombre}')
    return True


def red():
    if not existe('network', 'ppi115net'):
        docker('network', 'create', '--label', f'{LABEL}={VALUE}', 'ppi115net')


def preparar(fuentes):
    carpeta = Path(tempfile.mkdtemp(prefix='cafefe-tercer-parcial-'))
    os.chmod(carpeta, 0o700)
    originales = ['Pasted markdown(1).md', 'producto_preciosugerido_20260922.sql',
                  'producto_tipo_producto_20260918(1).sql']
    texto = '\n;\n'.join('\n'.join(line for line in (fuentes / name).read_text().splitlines()
                               if not line.strip().startswith('```')) for name in originales)
    texto += "\n;\nDO $$ BEGIN IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public') <> 19 THEN RAISE EXCEPTION 'Se esperaban 19 tablas'; END IF; END $$;\n"
    cuentas = {}
    virtual = None
    for rol in ('barista', 'cocinero', 'cajero', 'camarero'):
        eid, rid, aid = (str(uuid.uuid4()) for _ in range(3))
        nombre = f'Prueba {rol}'
        cuentas[rol] = {'nombre': nombre, 'uuid': eid}
        # Exclusivamente fixtures en un cluster efímero, nunca empleados reales.
        texto += f"INSERT INTO empleado(id_empleado,nombre,activo) VALUES ('{eid}','{nombre}',true);\n"
        texto += f"INSERT INTO rol(id_rol,nombre,activo) VALUES ('{rid}','{rol}',true);\n"
        texto += f"INSERT INTO empleado_rol(id_empleado_rol,id_empleado,id_rol,activo) VALUES ('{aid}','{eid}','{rid}',true);\n"
        if rol == 'camarero':
            virtual = aid
    producto, tipo, caja = (str(uuid.uuid4()) for _ in range(3))
    texto += f"INSERT INTO tipo_producto(id_tipo_producto,nombre,activo) VALUES ('{tipo}','Categoría de prueba',true);\n"
    texto += f"INSERT INTO producto(id_producto,nombre,precio_sugerido,activo) VALUES ('{producto}','Café de prueba',3.50,true);\n"
    texto += f"INSERT INTO producto_tipo_producto(id_producto_tipo_producto,id_producto,id_tipo_producto) VALUES ('{uuid.uuid4()}','{producto}','{tipo}');\n"
    texto += f"INSERT INTO caja(id_caja,nombre,activo) VALUES ('{caja}','Caja de prueba',true);\n"
    # SQL debe poder leerlo postgres dentro del contenedor; el directorio anfitrión es privado.
    (carpeta / '01-oficial.sql').write_text(texto)
    os.chmod(carpeta / '01-oficial.sql', 0o644)
    clave = secrets.token_hex(24)
    (carpeta / 'postgres.env').write_text(f'POSTGRES_USER=prueba_oficial\nPOSTGRES_PASSWORD={clave}\nPOSTGRES_DB=cafefe_pruebas\n')
    (carpeta / 'backend.env').write_text(f'PG_USER=prueba_oficial\nPG_PASS={clave}\nPG_DB=cafefe_pruebas\nPG_HOST=cafefe.db.pruebas\nPG_PORT=5432\nCAFEFE_FRONTEND_ORIGIN=http://localhost:18080\nCAFEFE_CAMARERO_VIRTUAL_ID={virtual}\n')
    (carpeta / 'cuentas.json').write_text(json.dumps(cuentas, indent=2))
    for name in ('postgres.env', 'backend.env', 'cuentas.json'):
        os.chmod(carpeta / name, 0o600)
    (carpeta / 'entorno.json').write_text(json.dumps({'tipo': VALUE, 'base': 'cafefe_pruebas'}))
    print(f'Preparado: {carpeta}\nCuentas sintéticas privadas: cuentas.json. No se inició ningún contenedor.')


def iniciar(carpeta, frontend):
    if json.loads((carpeta / 'entorno.json').read_text()) != {'tipo': VALUE, 'base': 'cafefe_pruebas'}:
        raise SystemExit('Directorio de pruebas no reconocido')
    # No aceptar un archivo editado para apuntar a PostgreSQL habitual.
    variables = dict(line.split('=', 1) for line in (carpeta / 'backend.env').read_text().splitlines())
    for key, value in {'PG_HOST': 'cafefe.db.pruebas', 'PG_DB': 'cafefe_pruebas', 'PG_PORT': '5432'}.items():
        if variables.get(key) != value:
            raise SystemExit('Este comando admite únicamente su PostgreSQL desechable')
    for nombre in NOMBRES:
        if existe('container', nombre):
            raise SystemExit(f'Nombre ocupado: {nombre}. No se reemplazará.')
    war = ROOT / 'target/Cafefe_PPI_2026-1.0-SNAPSHOT.war'
    if not war.is_file() or not (frontend / 'src/index.html').is_file():
        raise SystemExit('Falta WAR compilado o frontend src autónomo')
    red()
    etiqueta = ['--label', f'{LABEL}={VALUE}', '--network', 'ppi115net']
    docker('run', '-d', '--name', 'cafefe.db.pruebas', *etiqueta,
           '--env-file', carpeta / 'postgres.env', '--tmpfs', '/var/lib/postgresql/data',
           '--mount', f'type=bind,src={carpeta}/01-oficial.sql,dst=/docker-entrypoint-initdb.d/01-oficial.sql,readonly',
           'postgres:16-bookworm')
    for _ in range(90):
        # Comprobar la tabla final evita confundir el servidor temporal de initdb con el definitivo.
        tcp = subprocess.run(['docker', 'exec', 'cafefe.db.pruebas', 'pg_isready', '-h', '127.0.0.1'], capture_output=True)
        ready = subprocess.run(['docker', 'exec', 'cafefe.db.pruebas', 'psql', '-U',
                                'prueba_oficial', '-d', 'cafefe_pruebas', '-Atc',
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"],
                               capture_output=True, text=True)
        if tcp.returncode == 0 and ready.returncode == 0 and ready.stdout.strip() == '19':
            break
        time.sleep(1)
    else:
        raise SystemExit('PostgreSQL no inició. Consultar logs; detener solo con este script.')
    docker('run', '-d', '--name', 'cafefe.backend', '--hostname', 'cafefe.backend', *etiqueta,
           '-p', '127.0.0.1:19080:9080', '--env-file', carpeta / 'backend.env',
           '--mount', f'type=bind,src={war},dst=/opt/war/cafefe.war,readonly', 'cafefe-backend:ppi115-tercer-parcial')
    docker('run', '-d', '--name', 'cafefe.frontend', '--hostname', 'cafefe.frontend', *etiqueta,
           '-p', '127.0.0.1:18080:80', '--mount', f'type=bind,src={frontend}/src,dst=/var/www/html,readonly',
           'cafefe-frontend:ppi115-tercer-parcial')
    print('Entorno aislado iniciado: http://localhost:18080 . Aún no certifica los flujos.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('accion', choices=['construir', 'preparar', 'red', 'iniciar', 'logs', 'comprobar', 'detener', 'limpiar'])
    parser.add_argument('--frontend', type=Path, default=ROOT.parent / 'Cafefe_Fronten_2026')
    parser.add_argument('--fuentes', type=Path, default=Path.home() / 'Downloads')
    parser.add_argument('--entorno', type=Path)
    parser.add_argument('--contenedor', choices=NOMBRES, default='cafefe.backend')
    args = parser.parse_args()
    if args.accion == 'construir':
        docker('build', '-f', ROOT / 'Dockerfile.backend', '-t', 'cafefe-backend:ppi115-tercer-parcial', ROOT)
        docker('build', '-f', args.frontend / 'Dockerfile.frontend', '-t', 'cafefe-frontend:ppi115-tercer-parcial', args.frontend)
    elif args.accion == 'preparar':
        preparar(args.fuentes)
    elif args.accion == 'red':
        red()
    elif args.accion == 'iniciar':
        if not args.entorno:
            parser.error('iniciar requiere --entorno creado con preparar')
        iniciar(args.entorno.resolve(), args.frontend.resolve())
    elif args.accion == 'logs':
        if propio(args.contenedor):
            docker('logs', '--tail', '100', args.contenedor)
    elif args.accion == 'comprobar':
        for nombre in NOMBRES:
            if not propio(nombre):
                raise SystemExit(f'Falta {nombre}')
        for url in ('http://localhost:18080/index.html', 'http://localhost:18080/config/api.js',
                    'http://localhost:18080/cafefe/api/tipos-producto'):
            with urlopen(url, timeout=15) as respuesta:
                print(url, respuesta.status, respuesta.headers.get('Content-Type'))
    elif args.accion in ('detener', 'limpiar'):
        # Revisar todos antes de detener alguno.
        presentes = [nombre for nombre in NOMBRES if propio(nombre)]
        for nombre in presentes:
            docker('rm', '-f', nombre)
        if args.accion == 'limpiar' and existe('network', 'ppi115net'):
            info = json.loads(docker('network', 'inspect', 'ppi115net', capturar=True))[0]
            if info.get('Labels', {}).get(LABEL) == VALUE and not info.get('Containers'):
                docker('network', 'rm', 'ppi115net')
            else:
                print('Se conserva ppi115net: red ajena o con otros contenedores.')


if __name__ == '__main__':
    main()
