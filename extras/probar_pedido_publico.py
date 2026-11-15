#!/usr/bin/env python3
"""Prueba POST público con producto/orden propios y limpieza final; no altera catálogo real."""
import json
from types import SimpleNamespace
import urllib.request
from probar_api_facturas import Pruebas

BASE='http://127.0.0.1:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'
def main():
    db=Pruebas(SimpleNamespace(raiz=BASE))
    producto=None
    try:
        producto=db.sql("INSERT INTO producto(id_producto,nombre,activo,precio_sugerido) VALUES(gen_random_uuid(),'PRUEBA_TEMPORAL_PEDIDO_WEB',true,3.25) RETURNING id_producto;")
        db.filas.append(('producto',producto))
        datos={'nombreCliente':'PRUEBA_TEMPORAL_HU2','productos':[{'idProducto':producto,'cantidad':2,'observaciones':'Control de integración'}]}
        req=urllib.request.Request(BASE+'/ordenes/publicas',data=json.dumps(datos).encode(),headers={'Origin':'http://127.0.0.1:5501','Content-Type':'application/json'},method='POST')
        with urllib.request.urlopen(req,timeout=15) as respuesta:
            cuerpo=json.load(respuesta)
            assert respuesta.status==201, 'POST debe responder 201'
        db.filas.append(('orden',cuerpo['idOrden']))
        for fila in cuerpo['productos']: db.filas.append(('orden_producto',fila['idOrdenProducto']))
        assert len(cuerpo['productos'])==2 and cuerpo['total']==6.5
        assert all(p['idProducto']==producto and p['precio']==3.25 and 'PRUEBA_TEMPORAL_HU2' in p['observaciones'] for p in cuerpo['productos'])
        virtual=db.sql("SELECT er.id_empleado_rol FROM empleado_rol er JOIN empleado e USING(id_empleado) WHERE e.comentarios='CAFEFE_EMPLEADO_TECNICO_WEB_V1' AND er.observaciones='CAFEFE_ASIGNACION_WEB_V1';")
        assert cuerpo['idEmpleadoRol']==virtual, 'Debe asignarse al camarero técnico configurado'
        assert db.sql(f"SELECT count(*) FROM orden_producto WHERE id_orden='{cuerpo['idOrden']}';")=='2'
        print('OK POST público 201, camarero técnico, nombre, cantidades, precio y persistencia en PostgreSQL')
    finally:
        # Recoge únicamente órdenes creadas con nuestro producto incluso si falla el parsing HTTP.
        if producto:
            propias=db.sql(f"SELECT DISTINCT id_orden FROM orden_producto WHERE id_producto='{producto}';").splitlines()
            for orden in propias:
                if ('orden',orden) not in db.filas: db.filas.append(('orden',orden))
                filas=db.sql(f"SELECT id_orden_producto FROM orden_producto WHERE id_orden='{orden}' AND id_producto='{producto}';").splitlines()
                for fila in filas:
                    if ('orden_producto',fila) not in db.filas: db.filas.append(('orden_producto',fila))
        db.limpiar_filas()
if __name__=='__main__': main()
