#!/usr/bin/env python3
"""Pruebas REST y PostgreSQL de asignaciones de características a productos."""
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
import sys
import uuid

sys.dont_write_bytecode = True
from probar_api_caracteristicas import Pruebas, main


class Asignaciones(Pruebas):
    """Comprueba referencias, validación del valor, concurrencia y conservación de padres."""

    recurso = 'producto-caracteristicas'
    clave = 'idProductoCaracteristica'

    def casos(self):
        tipo = self.insertar('tipo_caracteristica', nombre=self.marca, activo=True,
                             expresion_regular='^[0-9]+$')
        caracteristica = self.insertar('caracteristica', nombre=self.marca, activo=True,
                                      id_tipo_caracteristica=tipo)
        inactiva = self.insertar('caracteristica', nombre=self.marca, activo=False,
                                 id_tipo_caracteristica=tipo)
        producto = self.insertar('producto', nombre=self.marca, activo=False, precio_sugerido=1)
        cuerpo = dict(idCaracteristica=caracteristica, idProducto=producto, valor='600')
        falta = str(uuid.uuid4())
        filtro = '/productos/' + producto
        self.comprobar('GET lista general', 'GET', '', None, 200, lambda v: isinstance(v, list))
        self.comprobar('GET filtro vacío', 'GET', '/productos/' + falta, None, 200, lambda v: v == [])
        self.comprobar('GET filtro inválido', 'GET', '/productos/mal', None, 400)
        self.comprobar('GET inexistente', 'GET', '/' + falta, None, 404)
        self.comprobar('DELETE inexistente', 'DELETE', '/' + falta, None, 404)
        self.comprobar('PUT no expuesto', 'PUT', '/' + falta, cuerpo, 405)
        for nombre, datos, codigo in (
                ('JSON inválido', b'[]', 400),
                ('valor no textual', dict(cuerpo, valor=600), 400),
                ('producto inexistente', dict(cuerpo, idProducto=falta), 422),
                ('característica inexistente', dict(cuerpo, idCaracteristica=falta), 422),
                ('característica inactiva', dict(cuerpo, idCaracteristica=inactiva), 422),
                ('valor vacío', dict(cuerpo, valor=' '), 422),
                ('valor no coincide con expresión', dict(cuerpo, valor='600 ml'), 422)):
            self.comprobar('POST ' + nombre, 'POST', '', datos, codigo)
        barrera = Barrier(2)
        def crear(_):
            barrera.wait(timeout=10)
            return self.pedir('POST', '', cuerpo)
        with ThreadPoolExecutor(max_workers=2) as executor:
            respuestas = list(executor.map(crear, range(2)))
        self.resultado('Dos altas concurrentes dan 201 y 409',
                       sorted(codigo for codigo, _ in respuestas) == [201, 409], respuestas)
        for codigo, dto in respuestas:
            if codigo != 201:
                continue
            ident = str(uuid.UUID(dto[self.clave]))
            self.comprobar('GET conserva referencias y valor', 'GET', '/' + ident, None, 200,
                           lambda v: v['idProducto'] == producto and v['idCaracteristica'] == caracteristica
                           and v['valor'] == '600')
            self.comprobar('GET filtro contiene una asociación', 'GET', filtro, None, 200,
                           lambda v: len(v) == 1 and v[0][self.clave] == ident)
            self.comprobar('POST duplicado', 'POST', '', cuerpo, 409)
            consulta = (f"SELECT row_to_json(p) FROM producto p WHERE id_producto='{producto}';"
                        f"SELECT row_to_json(c) FROM caracteristica c WHERE id_caracteristica='{caracteristica}';")
            padres = self.sql(consulta)
            self.borrar(ident)
            self.resultado('DELETE conserva ambos padres', padres == self.sql(consulta))


if __name__ == '__main__':
    sys.exit(main(Asignaciones))
