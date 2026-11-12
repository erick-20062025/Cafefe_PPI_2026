#!/usr/bin/env python3
"""Pruebas HTTP para Empleado en Cafefe (SOLO DESARROLLO).

Prueba CRUD, campos vacíos, tipos incorrectos y protección de relaciones.
Crea un empleado con etiqueta única y lo elimina al terminar. Para comprobar
el conflicto de roles, crea una relación TEMPORAL usando un rol ya activo,
sin modificar ni eliminar ese rol.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE_DEFECTO = 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'


class Pruebas:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.marca = 'PRUEBA_REST_EMPLEADO_' + uuid.uuid4().hex[:16].upper()
        self.ok = 0
        self.fallidas = 0
        self.omitidas = 0
        self.id_empleado = None
        self.id_rol = None
        self.id_relacion = None

    def pedir(self, metodo, ruta, cuerpo=None):
        datos = None if cuerpo is None else (
            cuerpo if isinstance(cuerpo, bytes)
            else json.dumps(cuerpo, ensure_ascii=False).encode('utf-8'))
        peticion = urllib.request.Request(self.base + ruta, data=datos, method=metodo,
            headers={'Content-Type': 'application/json', 'Accept': 'application/json'})
        try:
            with urllib.request.urlopen(peticion, timeout=15) as respuesta:
                estado = respuesta.status
                texto = respuesta.read().decode('utf-8', 'replace')
        except urllib.error.HTTPError as ex:
            estado = ex.code
            texto = ex.read().decode('utf-8', 'replace')
        except (urllib.error.URLError, TimeoutError) as ex:
            return 0, {'conexion': str(ex)}
        try:
            return estado, json.loads(texto) if texto else None
        except ValueError:
            return estado, texto[:250]

    def verificar(self, nombre, metodo, ruta, cuerpo, esperado, condicion=None):
        codigo, respuesta = self.pedir(metodo, ruta, cuerpo)
        api_codigo = (esperado < 400 or (isinstance(respuesta, dict)
            and respuesta.get('codigo') == f'API-{esperado}'
            and isinstance(respuesta.get('mensaje'), str)
            and bool(respuesta['mensaje'])
            and isinstance(respuesta.get('detalle'), str)))
        bien = codigo == esperado and api_codigo and (
            condicion is None or condicion(respuesta))
        if bien:
            self.ok += 1
            print(f'OK      {nombre:<50} HTTP {codigo}')
        else:
            self.fallidas += 1
            print(f'REVISAR {nombre:<50} HTTP {codigo}, esperado {esperado}: {str(respuesta)[:260]}')
        return codigo, respuesta

    def marcar(self, nombre, bien, detalle=''):
        if bien:
            self.ok += 1
            print('OK      ' + nombre)
        else:
            self.fallidas += 1
            print('REVISAR ' + nombre + (' — ' + detalle if detalle else ''))

    def omitida(self, nombre, motivo):
        self.omitidas += 1
        print('OMITIR  ' + nombre + ' — ' + motivo)

    def cuerpo(self, **cambios):
        datos = {'nombre': self.marca, 'apellido': 'Prueba',
                 'activo': True, 'comentarios': 'Registro temporal de pruebas REST'}
        datos.update(cambios)
        return datos

    def negativas(self):
        no_existe = str(uuid.uuid4())
        for nombre, metodo, ruta, cuerpo, esperado in [
            ('GET lista de empleados', 'GET', '/empleados', None, 200),
            ('GET UUID incorrecto', 'GET', '/empleados/incorrecto', None, 400),
            ('GET empleado inexistente', 'GET', '/empleados/' + no_existe, None, 404),
            ('PUT UUID incorrecto', 'PUT', '/empleados/incorrecto', self.cuerpo(), 400),
            ('PUT empleado inexistente', 'PUT', '/empleados/' + no_existe, self.cuerpo(), 404),
            ('DELETE UUID incorrecto', 'DELETE', '/empleados/incorrecto', None, 400),
            ('DELETE empleado inexistente', 'DELETE', '/empleados/' + no_existe, None, 404),
        ]:
            self.verificar(nombre, metodo, ruta, cuerpo, esperado,
                (lambda r: isinstance(r, list)) if nombre == 'GET lista de empleados' else None)
        sin_nombre = self.cuerpo(); del sin_nombre['nombre']
        sin_apellido = self.cuerpo(); del sin_apellido['apellido']
        sin_activo = self.cuerpo(); del sin_activo['activo']
        casos = [
            ('POST sin JSON', b'', 400),
            ('POST JSON malformado', b'{', 400),
            ('POST arreglo JSON', b'[]', 400),
            ('POST objeto vacío', {}, 422),
            ('POST sin nombre', sin_nombre, 422),
            ('POST nombre null', self.cuerpo(nombre=None), 422),
            ('POST nombre en blanco', self.cuerpo(nombre='  \t '), 422),
            ('POST nombre de un carácter', self.cuerpo(nombre='a'), 422),
            ('POST nombre demasiado largo', self.cuerpo(nombre='x'*256), 422),
            ('POST nombre tipo número', self.cuerpo(nombre=123), 400),
            ('POST sin apellido', sin_apellido, 422),
            ('POST apellido null', self.cuerpo(apellido=None), 422),
            ('POST apellido en blanco', self.cuerpo(apellido=' \t '), 422),
            ('POST apellido de un carácter', self.cuerpo(apellido='x'), 422),
            ('POST apellido demasiado largo', self.cuerpo(apellido='x'*256), 422),
            ('POST apellido tipo booleano', self.cuerpo(apellido=True), 400),
            ('POST sin activo', sin_activo, 422),
            ('POST activo null', self.cuerpo(activo=None), 422),
            ('POST activo tipo texto', self.cuerpo(activo='true'), 400),
            ('POST activo tipo número', self.cuerpo(activo=1), 400),
            ('POST comentarios tipo número', self.cuerpo(comentarios=3), 400),
            ('POST comentarios demasiado largos', self.cuerpo(comentarios='x'*1001), 422),
            ('POST nombre carácter control', self.cuerpo(nombre='An\x00a'), 422),
            ('POST apellido carácter control', self.cuerpo(apellido='Ga\x07rcía'), 422),
            ('POST cliente intenta asignar UUID', dict(self.cuerpo(), idEmpleado=no_existe), 400),
            ('POST cliente intenta asignar roles', dict(self.cuerpo(), empleadoRolCollection=[]), 400),
        ]
        for nombre, cuerpo, esperado in casos:
            self.verificar(nombre, 'POST', '/empleados', cuerpo, esperado)
        self.verificar('POST rechazados no crearon empleado', 'GET', '/empleados', None,
            200, lambda v: isinstance(v, list) and not any(
                isinstance(e, dict) and e.get('nombre') == self.marca for e in v))

    def positivas(self):
        codigo, respuesta = self.verificar('POST crear empleado temporal', 'POST',
            '/empleados', self.cuerpo(), 201,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca
            and v.get('apellido') == 'Prueba' and v.get('activo') is True
            and isinstance(v.get('idEmpleado'), str))
        if codigo == 201 and isinstance(respuesta, dict):
            candidato = respuesta.get('idEmpleado')
            try:
                self.id_empleado = str(uuid.UUID(candidato))
            except (TypeError, ValueError, AttributeError):
                self.marcar('POST devolvió UUID válido', False, str(candidato))
        if not self.id_empleado:
            self.omitida('Pruebas posteriores de CRUD', 'la creación no devolvió un UUID válido')
            return
        ruta = '/empleados/' + self.id_empleado
        self.verificar('GET empleado creado', 'GET', ruta, None, 200,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca)
        self.verificar('PUT nombre vacío no modifica', 'PUT', ruta,
            self.cuerpo(nombre=' '), 422)
        self.verificar('PUT apellido null no modifica', 'PUT', ruta,
            self.cuerpo(apellido=None), 422)
        self.verificar('PUT activo como texto no modifica', 'PUT', ruta,
            self.cuerpo(activo='false'), 400)
        self.verificar('PUT no permite cambiar UUID', 'PUT', ruta,
            dict(self.cuerpo(), idEmpleado=str(uuid.uuid4())), 400)
        self.verificar('GET conserva valores anteriores', 'GET', ruta, None, 200,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca
            and v.get('apellido') == 'Prueba' and v.get('activo') is True)
        self.verificar('PUT modifica empleado', 'PUT', ruta,
            self.cuerpo(nombre=self.marca+'_EDITADO', apellido='Validado', activo=False), 200,
            lambda v: isinstance(v, dict) and v.get('idEmpleado') == self.id_empleado
            and v.get('nombre') == self.marca+'_EDITADO' and v.get('apellido') == 'Validado'
            and v.get('activo') is False)
        self.verificar('GET confirma modificación', 'GET', ruta, None, 200,
            lambda v: isinstance(v, dict) and v.get('nombre') == self.marca+'_EDITADO'
            and v.get('activo') is False)
        self.verificar('PUT reactiva empleado', 'PUT', ruta,
            self.cuerpo(), 200,
            lambda v: isinstance(v, dict) and v.get('activo') is True)
        self.relacion_temporal()

    def relacion_temporal(self):
        if not self.id_empleado:
            return
        codigo, roles = self.pedir('GET', '/roles')
        if codigo != 200 or not isinstance(roles, list):
            self.omitida('Conflicto DELETE con roles', 'no se pudo consultar la API /roles')
            return
        validos = [r for r in roles if isinstance(r, dict)
            and r.get('activo') is True and isinstance(r.get('idRol'), str)]
        if not validos:
            self.omitida('Conflicto DELETE con roles', 'no hay rol activo en el catálogo')
            return
        self.id_rol = validos[0]['idRol']
        cuerpo = {'idEmpleado': self.id_empleado, 'idRol': self.id_rol,
                  'activo': True, 'observaciones': self.marca}
        codigo, respuesta = self.verificar('POST relación temporal para probar conflicto',
            'POST', '/empleado-roles', cuerpo, 201,
            lambda v: isinstance(v, dict) and v.get('idEmpleado') == self.id_empleado
            and v.get('idRol') == self.id_rol and isinstance(v.get('idEmpleadoRol'), str))
        if codigo != 201:
            self.omitida('DELETE protegido con rol', 'no se pudo crear la relación temporal')
            return
        self.id_relacion = respuesta.get('idEmpleadoRol')
        self.verificar('DELETE empleado con rol asociado', 'DELETE',
            '/empleados/' + self.id_empleado, None, 409)
        self.verificar('GET conserva empleado tras DELETE rechazado', 'GET',
            '/empleados/' + self.id_empleado, None, 200,
            lambda v: isinstance(v, dict) and v.get('idEmpleado') == self.id_empleado)

    def limpiar(self):
        # Recuperación por etiqueta cuando se creó un registro pero la respuesta se perdió.
        if not self.id_empleado:
            codigo, empleados = self.pedir('GET', '/empleados')
            if codigo == 200 and isinstance(empleados, list):
                coincidentes = [e['idEmpleado'] for e in empleados
                    if isinstance(e, dict) and isinstance(e.get('idEmpleado'), str)
                    and isinstance(e.get('nombre'), str)
                    and e['nombre'].startswith(self.marca)]
                if len(coincidentes) == 1:
                    self.id_empleado = coincidentes[0]
        if not self.id_empleado:
            return
        if self.id_relacion is None:
            codigo, relaciones = self.pedir('GET', '/empleado-roles/empleados/' + self.id_empleado)
            if codigo == 200 and isinstance(relaciones, list):
                candidatas = [r.get('idEmpleadoRol') for r in relaciones if isinstance(r, dict)
                    and r.get('idEmpleado') == self.id_empleado
                    and isinstance(r.get('observaciones'), str)
                    and r['observaciones'] == self.marca
                    and isinstance(r.get('idEmpleadoRol'), str)]
                if len(candidatas) == 1:
                    self.id_relacion = candidatas[0]
        if self.id_relacion:
            # La relacion pertenece al empleado temporal y fue identificada por etiqueta.
            codigo, relacion = self.pedir('GET', '/empleado-roles/' + self.id_relacion)
            if codigo == 200 and isinstance(relacion, dict) and (
                    relacion.get('idEmpleado') == self.id_empleado
                    and relacion.get('observaciones') == self.marca):
                self.verificar('Limpieza: eliminar relación temporal', 'DELETE',
                    '/empleado-roles/' + self.id_relacion, None, 200)
            else:
                self.marcar('Limpieza: relación no verificada; requiere revisión manual',
                    False, str(self.id_relacion))
        codigo, empleado = self.pedir('GET', '/empleados/' + self.id_empleado)
        if codigo == 404:
            self.marcar('Limpieza: empleado ya no existe', True)
            return
        if codigo != 200 or not isinstance(empleado, dict) or (
                not isinstance(empleado.get('nombre'), str)
                or not empleado['nombre'].startswith(self.marca)):
            self.marcar('Limpieza: identidad de empleado no comprobada', False,
                f'idEmpleado={self.id_empleado}, HTTP={codigo}')
            return
        estado, _ = self.verificar('Limpieza: eliminar empleado temporal', 'DELETE',
            '/empleados/' + self.id_empleado, None, 200)
        if estado == 200:
            self.verificar('Limpieza: GET confirma eliminación', 'GET',
                '/empleados/' + self.id_empleado, None, 404)
        else:
            print('ATENCIÓN: verificar manualmente empleado temporal', self.id_empleado)

    def ejecutar(self):
        print('API:', self.base + '/empleados')
        print('Etiqueta temporal:', self.marca)
        print('SOLO desarrollo. Crea y limpia únicamente su empleado y asignación temporales.')
        try:
            self.negativas()
            self.positivas()
        finally:
            self.limpiar()
        print(f'\nRESUMEN: {self.ok} OK / {self.fallidas} FALLIDAS / {self.omitidas} OMITIDAS')
        print('Las pruebas omitidas no cuentan como aprobadas.')
        return 0 if not self.fallidas else 1


def main():
    p = argparse.ArgumentParser(description='Pruebas REST de empleados (solo desarrollo)')
    p.add_argument('--base', default=BASE_DEFECTO, help='URL base que termina en /api')
    args = p.parse_args()
    return Pruebas(args.base).ejecutar()


if __name__ == '__main__':
    sys.exit(main())
