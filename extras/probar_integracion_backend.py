#!/usr/bin/env python3
"""Verifica el catálogo de tipos y el paso de Orden a Factura con datos propios."""
import argparse
import sys
import uuid
from types import SimpleNamespace
from urllib.parse import urlparse

sys.dont_write_bytecode = True
import probar_api_facturas as base


class Pruebas(base.Pruebas):
    def casos(self):
        catalogo = self.raiz + '/tipos-caracteristica'
        valido = dict(nombre=self.marca, activo=True, expresionRegular='^[0-9]+$')
        codigo, respuesta = self.pedir('POST', '', dict(valido, expresionRegular='['), raiz=catalogo)
        self.resultado('Rechaza expresión regular inválida', codigo == 422, respuesta)
        codigo, tipo = self.pedir('POST', '', valido, raiz=catalogo)
        self.resultado('Crea un tipo y devuelve su expresión', codigo == 201
                       and tipo.get('expresionRegular') == valido['expresionRegular'], tipo)
        if codigo != 201:
            raise RuntimeError('No se pudo crear el tipo de prueba')
        id_tipo = str(uuid.UUID(tipo['idTipoCaracteristica']))
        self.filas.append(('tipo_caracteristica', id_tipo))
        codigo, _ = self.pedir('PUT', '/' + id_tipo, dict(valido, activo=False), raiz=catalogo)
        self.resultado('Permite desactivar un tipo', codigo == 200)
        codigo, lista = self.pedir('GET', '', raiz=catalogo)
        self.resultado('El catálogo incluye el tipo desactivado', codigo == 200
                       and any(v['idTipoCaracteristica'] == id_tipo and not v['activo'] for v in lista))
        codigo, _ = self.pedir('DELETE', '/' + id_tipo, raiz=catalogo)
        self.resultado('Elimina el tipo sin referencias', codigo == 200)

        caja = self.insertar('caja', nombre=self.marca, activo=True)
        rol = self.insertar('rol', nombre=self.marca, activo=True)
        empleado = self.insertar('empleado', nombre=self.marca, apellido='Prueba', activo=True)
        asignacion = self.insertar('empleado_rol', id_rol=rol, id_empleado=empleado, activo=True)
        orden = self.insertar('orden', id_empleado_rol=asignacion)
        otra = self.insertar('orden', id_empleado_rol=asignacion)
        producto = self.insertar('producto', nombre=self.marca, activo=True, precio_sugerido=10)
        ordenes = self.raiz + '/ordenes'
        ruta = '/' + orden + '/productos'
        codigo, filas = self.pedir('POST', ruta, dict(idProducto=producto, cantidad=2), raiz=ordenes)
        self.resultado('Agrega dos filas al precio del catálogo', codigo == 201 and len(filas) == 2
                       and all(f['precio'] == 10 for f in filas), filas)
        if codigo != 201:
            raise RuntimeError('No se pudieron crear filas de prueba')
        ids = [str(uuid.UUID(f['idOrdenProducto'])) for f in filas]
        self.filas.extend(('orden_producto', id_fila) for id_fila in ids)
        codigo, _ = self.pedir('DELETE', '/' + otra + '/productos/' + ids[0], raiz=ordenes)
        self.resultado('No quita una fila de otra orden', codigo == 404)
        codigo, filas = self.pedir('GET', ruta + '-facturables', raiz=ordenes)
        self.resultado('Ambas filas están disponibles antes de facturar', codigo == 200 and len(filas) == 2)
        codigo, factura = self.pedir('POST', '', dict(idCaja=caja, idEmpleadoRol=asignacion,
            productos=[dict(idOrdenProducto=ids[0], precio=10)]))
        self.resultado('Factura devuelve la fila y sus datos de catálogo', codigo == 201
                       and factura['productos'][0]['idOrden'] == orden
                       and factura['productos'][0]['idProducto'] == producto
                       and factura['productos'][0]['producto'] == self.marca, factura)
        codigo, filas = self.pedir('GET', ruta + '-facturables', raiz=ordenes)
        self.resultado('El filtro excluye la fila incluso con factura pendiente', codigo == 200
                       and [f['idOrdenProducto'] for f in filas] == [ids[1]], filas)
        codigo, _ = self.pedir('POST', ruta, dict(idProducto=producto, cantidad=1), raiz=ordenes)
        self.resultado('No agrega filas a orden facturada', codigo == 409)
        codigo, _ = self.pedir('DELETE', ruta + '/' + ids[1], raiz=ordenes)
        self.resultado('No quita filas de orden facturada', codigo == 409)
        if 'idFactura' in factura:
            self.borrar(str(uuid.UUID(factura['idFactura'])))
        codigo, _ = self.pedir('DELETE', ruta + '/' + ids[1], raiz=ordenes)
        self.resultado('Quita la fila al liberar la factura', codigo == 200)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raiz', default=base.DEFAULT_ROOT)
    args = parser.parse_args()
    if urlparse(args.raiz).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Use la API local de desarrollo')
    base.TABLAS = (*base.TABLAS, 'tipo_caracteristica')
    sys.exit(Pruebas(SimpleNamespace(raiz=args.raiz)).ejecutar())
