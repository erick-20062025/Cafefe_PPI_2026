#!/usr/bin/env python3
"""Prueba de sesión contra Liberty local con filas propias, eliminadas al terminar.
No modifica empleados/roles existentes. UUID generados por PostgreSQL.
"""
import http.cookiejar
import json
from types import SimpleNamespace
import urllib.request
import urllib.error
from probar_api_facturas import Pruebas

BASE = 'http://127.0.0.1:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'
ORIGIN = 'http://127.0.0.1:5501'

def main():
    db = Pruebas(SimpleNamespace(raiz=BASE))
    jar = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    def pedir(method, path, data=None, origin=ORIGIN):
        headers = {'Content-Type':'application/json'}
        if origin: headers['Origin'] = origin
        req = urllib.request.Request(BASE+path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
        try: response = client.open(req, timeout=10)
        except urllib.error.HTTPError as error: response = error
        with response:
            raw = response.read()
            return response.status, response.headers, json.loads(raw) if raw else None
    def exigir(condition, label):
        if not condition: raise AssertionError(label)
        print('OK '+label, flush=True)
    def crear(tabla, columnas, valores):
        ident = db.sql(f"INSERT INTO {tabla} (id_{tabla},{columnas}) VALUES (gen_random_uuid(),{valores}) RETURNING id_{tabla};")
        db.filas.append((tabla,ident)); return ident
    try:
        # Si ya existe un cocinero, solo se reutiliza si es único y activo.
        roles = db.sql("SELECT id_rol::text || '|' || activo::text FROM rol WHERE lower(trim(nombre))='cocinero';").splitlines()
        if roles:
            if len(roles)!=1 or not roles[0].endswith('|true'): raise RuntimeError('Rol cocinero ambiguo o inactivo; no se modifica.')
            rol = roles[0].split('|')[0]
        else:
            rol = crear('rol','nombre,activo,observaciones',"'cocinero',true,'PRUEBA_TEMPORAL_HU1: eliminar al finalizar'")
        empleado = crear('empleado','nombre,apellido,activo,comentarios',"'Prueba temporal HU1','Integracion',true,'Solo prueba temporal de sesion'")
        asignacion = crear('empleado_rol','id_empleado,id_rol,activo',f"'{empleado}','{rol}',true")
        credenciales = {'nombre':'Prueba temporal HU1','uuid':empleado}
        exigir(pedir('GET','/sesion')[0]==401,'sin sesión devuelve 401')
        exigir(pedir('GET','/ordenes/pendientes')[0]==401,'Recepción bloquea acceso anónimo')
        exigir(pedir('POST','/sesion',credenciales,origin=None)[0]==403,'login rechaza origen ausente')
        exigir(pedir('POST','/sesion',dict(credenciales,nombre='Incorrecto'))[0]==401,'rechaza nombre incorrecto')
        code, headers, body = pedir('POST','/sesion',credenciales)
        exigir(code==200 and body['roles']==['cocinero'] and empleado not in json.dumps(body),'login real sin exponer UUID')
        exigir('no-store' in headers.get('Cache-Control','').lower(),'respuesta de sesión no almacenable: '+str(headers.get('Cache-Control')))
        cookie = headers.get('Set-Cookie','').lower()
        exigir('httponly' in cookie and 'samesite=lax' in cookie,'cookie HttpOnly y SameSite Lax')
        exigir(pedir('GET','/sesion')[0]==200,'recuperación real por cookie')
        exigir(pedir('GET','/ordenes/pendientes')[0]==200,'Recepción admite cocinero')
        db.sql(f"UPDATE empleado_rol SET activo=false WHERE id_empleado_rol='{asignacion}';")
        exigir(pedir('GET','/sesion')[0]==401,'revocación de asignación invalida sesión')
        db.sql(f"UPDATE empleado_rol SET activo=true WHERE id_empleado_rol='{asignacion}';")
        exigir(pedir('POST','/sesion',credenciales)[0]==200,'permite nueva sesión')
        exigir(pedir('DELETE','/sesion')[0]==204 and pedir('GET','/sesion')[0]==401,'cierre real invalida cookie')
        db.sql(f"UPDATE empleado SET activo=false WHERE id_empleado='{empleado}';")
        exigir(pedir('POST','/sesion',credenciales)[0]==401,'empleado inactivo rechazado')
    finally:
        try:
            pedir('DELETE','/sesion')
        finally:
            db.limpiar_filas()

if __name__ == '__main__': main()
