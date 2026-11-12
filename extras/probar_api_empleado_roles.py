#!/usr/bin/env python3
"""Pruebas de EmpleadoRol en base de DESARROLLO; crea y borra solo una relación temporal.
Reutiliza empleados/roles activos, no modifica sus registros. No usar en producción.
"""
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

URL = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/empleado-roles'

class Pruebas:
    def __init__(self, a):
        self.a = a
        self.url = a.base.rstrip('/')
        self.marca = 'PRUEBA_REST_EMPLEADOROL_' + uuid.uuid4().hex.upper()
        self.ok = self.malas = self.omitidas = 0
        self.creada = None
        self.empleado = self.rol = None

    def pedir(self, verbo, ruta='', cuerpo=None):
        data = None if cuerpo is None else (cuerpo if isinstance(cuerpo, bytes)
                else json.dumps(cuerpo, ensure_ascii=False).encode('utf-8'))
        req = urllib.request.Request(self.url + ruta, data=data, method=verbo,
                                     headers={'Content-Type':'application/json',
                                              'Accept':'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                codigo, texto = r.status, r.read().decode('utf-8', 'replace')
        except urllib.error.HTTPError as ex:
            codigo, texto = ex.code, ex.read().decode('utf-8', 'replace')
        except urllib.error.URLError as ex:
            return 0, {'conexion': str(ex)}
        try:
            return codigo, json.loads(texto) if texto else None
        except ValueError:
            return codigo, texto[:250]

    def verificar(self, nombre, bien, detalle=''):
        self.ok += int(bool(bien))
        self.malas += int(not bien)
        print(('OK      ' if bien else 'REVISAR ') + nombre)
        if not bien:
            print('        ' + str(detalle)[:350])

    def probar(self, nombre, metodo, ruta, cuerpo, esperado, extra=None):
        codigo, respuesta = self.pedir(metodo, ruta, cuerpo)
        con_mensaje = (esperado < 400 or
            (isinstance(respuesta, dict) and respuesta.get('codigo') == 'API-' + str(esperado)
             and bool(respuesta.get('mensaje')) and isinstance(respuesta.get('detalle'), str)))
        bien = codigo == esperado and con_mensaje and (extra is None or extra(respuesta))
        self.verificar(nombre, bien, f'HTTP {codigo} esperado {esperado}: {respuesta}')
        return codigo, respuesta

    def sql(self, consulta):
        p = subprocess.run(['docker','exec','-i',self.a.contenedor,'psql','-X','-q',
               '-t','-A','-F','\t','-v','ON_ERROR_STOP=1','-U',self.a.usuario,
               '-d',self.a.bd], input=consulta, text=True, capture_output=True, timeout=20)
        if p.returncode:
            raise RuntimeError('No se pudo consultar PostgreSQL: ' + p.stderr.strip()[:300])
        return [r for r in p.stdout.splitlines() if r.strip()]

    def contar(self):
        return int(self.sql('SELECT COUNT(*) FROM empleado_rol;')[0])

    def negativas(self):
        invalido, e, r = [str(uuid.uuid4()) for _ in range(3)]
        ejemplo = {'idEmpleado': e, 'idRol': r, 'activo': True, 'observaciones': self.marca}
        for nombre, metodo, ruta, cuerpo, status in [
            ('GET listar asignaciones','GET','',None,200),
            ('GET por empleado UUID inválido','GET','/empleados/incorrecto',None,400),
            ('GET asignación inexistente','GET','/'+invalido,None,404),
            ('GET UUID inválido','GET','/incorrecto',None,400),
            ('PUT UUID inválido','PUT','/incorrecto',{'activo':True},400),
            ('PUT asignación inexistente','PUT','/'+invalido,{'activo':True},404),
            ('DELETE UUID inválido','DELETE','/incorrecto',None,400),
            ('DELETE asignación inexistente','DELETE','/'+invalido,None,404),
        ]:
            self.probar(nombre, metodo, ruta, cuerpo, status)
        antes = self.contar()
        casos = [
            ('POST sin JSON',b'',400), ('POST JSON malformado',b'{',400),
            ('POST arreglo JSON',b'[]',400), ('POST objeto vacío',{},422),
            ('POST sin empleado',{'idRol':r,'activo':True},422),
            ('POST empleado null',{**ejemplo,'idEmpleado':None},422),
            ('POST empleado blanco',{**ejemplo,'idEmpleado':'   '},422),
            ('POST empleado tipo número',{**ejemplo,'idEmpleado':7},400),
            ('POST empleado UUID incorrecto',{**ejemplo,'idEmpleado':'no-uuid'},400),
            ('POST sin rol',{'idEmpleado':e,'activo':True},422),
            ('POST rol null',{**ejemplo,'idRol':None},422),
            ('POST rol tipo booleano',{**ejemplo,'idRol':False},400),
            ('POST rol UUID incorrecto',{**ejemplo,'idRol':'mal-uuid'},400),
            ('POST sin activo',{'idEmpleado':e,'idRol':r},422),
            ('POST activo null',{**ejemplo,'activo':None},422),
            ('POST activo texto',{**ejemplo,'activo':'true'},400),
            ('POST activo número',{**ejemplo,'activo':1},400),
            ('POST observaciones número',{**ejemplo,'observaciones':2},400),
            ('POST observaciones largas',{**ejemplo,'observaciones':'x'*1001},422),
            ('POST observaciones control',{**ejemplo,'observaciones':'a\x00b'},422),
            ('POST cliente elige ID',{**ejemplo,'idEmpleadoRol':invalido},400),
            ('POST intenta enviar órdenes',{**ejemplo,'ordenCollection':[]},400),
        ]
        for nombre, body, code in casos:
            self.probar(nombre,'POST','',body,code)
        self.verificar('POST inválidos no crearon relaciones', antes == self.contar(),
                       'Antes y después deben coincidir')

    def positivas(self):
        filas = self.sql('''SELECT e.id_empleado::text,r.id_rol::text
            FROM empleado e CROSS JOIN rol r
            WHERE e.activo IS TRUE AND r.activo IS TRUE
            AND NOT EXISTS (SELECT 1 FROM empleado_rol er WHERE er.id_empleado=e.id_empleado
                AND er.id_rol=r.id_rol)
            ORDER BY e.id_empleado,r.id_rol LIMIT 1;''')
        if not filas:
            self.omitidas += 1
            print('OMITIR  CRUD positivo: no hay pareja empleado/rol activos sin asignación')
            return
        self.empleado, self.rol = filas[0].split('\t')
        body = {'idEmpleado':self.empleado,'idRol':self.rol,'activo':True,
                'observaciones':self.marca}
        code, respuesta = self.probar('POST crear relación temporal','POST','',body,201,
            lambda v: isinstance(v,dict) and v.get('idEmpleado')==self.empleado
                      and v.get('idRol')==self.rol and v.get('activo') is True
                      and bool(v.get('idEmpleadoRol')))
        if code != 201 or not isinstance(respuesta,dict) or not respuesta.get('idEmpleadoRol'):
            self.omitidas += 1
            print('OMITIR  Continuar CRUD: POST no creó la relación')
            return
        self.creada = respuesta['idEmpleadoRol']
        try:
            uuid.UUID(self.creada)
        except (ValueError,TypeError):
            self.verificar('UUID de asignación creada válido',False,self.creada)
            return
        self.verificar('UUID de asignación creada válido',True)
        ruta = '/' + self.creada
        self.probar('GET asignación recién creada','GET',ruta,None,200,
            lambda v:isinstance(v,dict) and v.get('idEmpleadoRol')==self.creada)
        self.probar('GET roles asociados solo al empleado','GET','/empleados/'+self.empleado,None,200,
            lambda v:isinstance(v,list) and any(isinstance(x,dict)
                 and x.get('idEmpleadoRol')==self.creada for x in v))
        self.probar('POST relación duplicada','POST','',body,409)
        self.probar('PUT impide cambiar empleado','PUT',ruta,
                    {'activo':False,'idEmpleado':self.empleado},400)
        self.probar('PUT activo null','PUT',ruta,{'activo':None},422)
        self.probar('GET tras PUT inválido conserva activo','GET',ruta,None,200,
                    lambda v:isinstance(v,dict) and v.get('activo') is True)
        self.probar('PUT desactivar relación','PUT',ruta,
                    {'activo':False,'observaciones':self.marca+'_OFF'},200,
                    lambda v:isinstance(v,dict) and v.get('activo') is False)
        self.probar('GET confirma desactivación','GET',ruta,None,200,
                    lambda v:isinstance(v,dict) and v.get('activo') is False)
        self.probar('PUT reactivar relación','PUT',ruta,
                    {'activo':True,'observaciones':self.marca},200,
                    lambda v:isinstance(v,dict) and v.get('activo') is True)

    def limpiar(self):
        if not self.creada:
            return
        ruta = '/' + self.creada
        status, v = self.pedir('GET',ruta)
        if (status != 200 or not isinstance(v,dict)
            or v.get('idEmpleado')!=self.empleado or v.get('idRol')!=self.rol
            or not isinstance(v.get('observaciones'),str)
            or not v['observaciones'].startswith(self.marca)):
            self.verificar('Limpieza restringida a relación propia',False,
                          f'Revisar manualmente {self.creada}: {v}')
            return
        self.probar('Limpieza DELETE solo relación temporal','DELETE',ruta,None,200)
        self.probar('Limpieza GET confirma eliminación','GET',ruta,None,404)

    def run(self):
        print('API:',self.url,'\nEtiqueta:',self.marca,'\nSolo usar en DESARROLLO.')
        try:
            self.negativas()
            self.positivas()
        except (OSError,RuntimeError,ValueError,subprocess.TimeoutExpired) as ex:
            self.verificar('Ejecución',False,str(ex))
        finally:
            self.limpiar()
            print(f'\nRESUMEN: {self.ok} OK / {self.malas} FALLIDAS / {self.omitidas} OMITIDAS')
        return int(bool(self.malas))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',default=URL)
    parser.add_argument('--contenedor',default='postgres-universidad')
    parser.add_argument('--usuario',default='erick')
    parser.add_argument('--bd',default='programacion_internet')
    sys.exit(Pruebas(parser.parse_args()).run())
