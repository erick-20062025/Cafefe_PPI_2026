#!/usr/bin/env python3
"""Integración local: solo crea y elimina datos temporales propios; no imprime credenciales."""
import json, http.cookiejar, urllib.request, urllib.error
from datetime import datetime, timezone, timedelta
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from types import SimpleNamespace
from probar_api_facturas import Pruebas
BASE='http://127.0.0.1:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api'
ORIGIN='http://127.0.0.1:5501'
def main():
 db=Pruebas(SimpleNamespace(raiz=BASE)); filas=[]; ordenes=[]; facturas=[]; n=0
 client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 def crear(t,c,v):
  i=db.sql(f'INSERT INTO {t}(id_{t},{c}) VALUES(gen_random_uuid(),{v}) RETURNING id_{t};');filas.append((t,i));return i
 def pedir(m,p,d=None,cliente=None):
  q=urllib.request.Request(BASE+p,data=None if d is None else json.dumps(d).encode(),headers={'Origin':ORIGIN,'Content-Type':'application/json'},method=m)
  try: r=(cliente or client).open(q,timeout=20)
  except urllib.error.HTTPError as e:r=e
  with r:
   raw=r.read();return r.status,json.loads(raw) if raw else None
 def ok(c,label):
  nonlocal n
  assert c,label
  n+=1;print('OK '+label,flush=True)
 def login(nombre,ident):
  nonlocal client
  client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
  ok(pedir('POST','/sesion',{'nombre':nombre,'uuid':ident})[0]==200,'sesión '+nombre)
 try:
  prod=crear('producto','nombre,activo,precio_sugerido',"'PRUEBA_TEMPORAL_FLUJO',true,5.00")
  car=crear('caracteristica','nombre,activo',"'Presentación temporal',true")
  crear('producto_caracteristica','id_producto,id_caracteristica,valor',f"'{prod}','{car}','12 oz'")
  tipo=crear('tipo_descuento','nombre,activo,descuento_maximo',"'PRUEBA_TEMPORAL_FLUJO',true,100")
  promo=crear('descuento','nombre,id_tipo_descuento,fecha_desde,fecha_hasta',f"'PRUEBA_TEMPORAL_FLUJO','{tipo}',now()-interval '1 day',now()+interval '1 day'")
  dp=crear('descuento_producto','id_producto,id_descuento,valor,fecha_desde,fecha_hasta',f"'{prod}','{promo}',20,now()-interval '1 day',now()+interval '1 day'")
  personal={}
  for rol in ['camarero','cocinero','cajero']:
   ids=db.sql(f"SELECT id_rol FROM rol WHERE lower(trim(nombre))='{rol}' AND activo=true;").splitlines()
   if not ids: ids=[crear('rol','nombre,activo',f"'{rol}',true")]
   if len(ids)!=1:raise RuntimeError('Rol activo ambiguo '+rol)
   nombre='Prueba temporal '+rol
   empleado=crear('empleado','nombre,apellido,activo',f"'{nombre}','Flujo',true")
   er=crear('empleado_rol','id_empleado,id_rol,activo',f"'{empleado}','{ids[0]}',true")
   personal[rol]=(nombre,empleado,er)
  caja=crear('caja','nombre,activo',"'PRUEBA_TEMPORAL_FLUJO',true")
  datos={'nombreCliente':'Prueba temporal cliente','productos':[{'idProducto':prod,'cantidad':2,'observaciones':'Sin azúcar'}]}
  code,cot=pedir('POST','/ordenes/cotizacion',datos);ok(code==200 and cot['subtotal']==10 and cot['descuento']==2 and cot['total']==8,'cotización automática por cantidades')
  code,p=pedir('GET','/productos/'+prod);ok(code==200 and '12 oz' in p['caracteristicas'],'características reales del catálogo')
  code,publica=pedir('POST','/ordenes/publicas',datos)
  ok(code==201,'pedido público creado')
  ordenes.append(publica['idOrden'])
  ok(publica['total']==8 and publica['descuento']==2 and 'Cafefe Web' in publica['atendidoPor'],'virtual e importes históricos')
  login(*personal['camarero'][:2])
  code,orden=pedir('POST','/ordenes/publicas',datos);ok(code==201,'pedido de camarero creado');oid=orden['idOrden'];ordenes.append(oid)
  ok(orden['idEmpleadoRol']==personal['camarero'][2] and 'Prueba temporal camarero' in orden['atendidoPor'],'identidad del camarero desde sesión')
  ok(pedir('PUT',f'/ordenes/{oid}/preparacion',{'esperado':'PENDIENTE','siguiente':'EN_PREPARACION'})[0]==403,'camarero no prepara órdenes')
  ahora=datetime.now(timezone.utc)
  code,_=pedir('PUT','/descuentos/'+promo,{'nombre':'PRUEBA_TEMPORAL_VENCIDA','idTipoDescuento':tipo,'fechaDesde':(ahora-timedelta(days=1)).isoformat(),'fechaHasta':(ahora-timedelta(hours=1)).isoformat()})
  ok(code==200,'editar promoción mediante su API')
  code,cot=pedir('POST','/ordenes/cotizacion',datos);ok(code==200 and cot['total']==10,'promoción vencida no se aplica')
  ok(db.sql(f"SELECT sum(precio)::text FROM orden_producto WHERE id_orden='{oid}';")=='8.00','editar promoción conserva precio histórico')
  # Cancelación: todo dato de este bloque se identifica y limpia por UUID propio.
  cam=client
  def nueva():
   code,o=pedir('POST','/ordenes/publicas',datos,cliente=cam);assert code==201;ordenes.append(o['idOrden']);return o
  cancelable=nueva();cid=cancelable['idOrden']
  anon=urllib.request.build_opener()
  ok(pedir('POST',f'/ordenes/{cid}/cancelacion',cliente=anon)[0]==401,'anónimo no puede cancelar')
  otro=crear('empleado','nombre,apellido,activo',"'Prueba temporal otro','Flujo',true")
  rolcam=db.sql("SELECT id_rol FROM rol WHERE lower(trim(nombre))='camarero' AND activo=true;")
  crear('empleado_rol','id_empleado,id_rol,activo',f"'{otro}','{rolcam}',true")
  login('Prueba temporal otro',otro)
  ok(pedir('POST',f'/ordenes/{cid}/cancelacion')[0]==403,'camarero ajeno no puede cancelar')
  ok(pedir('POST',f'/ordenes/{cid}/cancelacion',cliente=cam)[0]==200,'propietario cancela pendiente')
  ok(pedir('POST',f'/ordenes/{cid}/cancelacion',cliente=cam)[0]==409,'segunda cancelación rechazada')
  ok(db.sql(f"SELECT count(*) FROM orden_producto WHERE id_orden='{cid}';")=='2','cancelación conserva productos')
  login(*personal['cajero'][:2])
  ok(pedir('POST',f'/ordenes/{oid}/cancelacion')[0]==403,'cajero no puede cancelar')
  ok(pedir('POST',f'/cobros/ordenes/{cid}/facturas',{'idCaja':caja})[0]==409,'cancelada no se factura')
  ok(pedir('POST',f'/cobros/ordenes/{oid}/facturas',{'idCaja':caja})[0]==409,'pendiente no se factura')
  caj=client
  login(*personal['cocinero'][:2])
  coc=client
  ok(pedir('POST',f'/ordenes/{oid}/cancelacion')[0]==403,'cocinero no puede cancelar')
  ok(pedir('PUT',f'/ordenes/{cid}/preparacion',{'esperado':'CANCELADA','siguiente':'EN_PREPARACION'})[0]==409,'cancelada no se prepara')
  _,pendientes=pedir('GET','/ordenes/pendientes');ok(all(o['idOrden']!=cid for o in pendientes),'cancelada fuera de Recepción')
  concurrente=nueva()['idOrden'];barrera=Barrier(2)
  def carrera(m,p,d,cl):barrera.wait();return pedir(m,p,d,cliente=cl)[0]
  with ThreadPoolExecutor(2) as ex:
   a=ex.submit(carrera,'POST',f'/ordenes/{concurrente}/cancelacion',None,cam)
   b=ex.submit(carrera,'POST',f'/ordenes/{concurrente}/cancelacion',None,cam)
   ok(sorted([a.result(),b.result()])==[200,409],'dos cancelaciones simultáneas: solo una gana')
  concurrente=nueva()['idOrden'];barrera=Barrier(2)
  with ThreadPoolExecutor(2) as ex:
   a=ex.submit(carrera,'POST',f'/ordenes/{concurrente}/cancelacion',None,cam)
   b=ex.submit(carrera,'PUT',f'/ordenes/{concurrente}/preparacion',{'esperado':'PENDIENTE','siguiente':'EN_PREPARACION'},coc)
   ok(sorted([a.result(),b.result()])==[200,409],'cancelación frente a preparación: solo una gana')
  for anterior,nuevo in [('PENDIENTE','EN_PREPARACION'),('EN_PREPARACION','LISTA'),('LISTA','DESPACHADA')]:
   ok(pedir('PUT',f'/ordenes/{oid}/preparacion',{'esperado':anterior,'siguiente':nuevo})[0]==200,'preparación '+nuevo)
   ok(pedir('POST',f'/ordenes/{oid}/cancelacion',cliente=cam)[0]==409,'cancelación rechazada en '+nuevo)
   if nuevo!='DESPACHADA':ok(pedir('POST',f'/cobros/ordenes/{oid}/facturas',{'idCaja':caja},cliente=caj)[0]==409,'facturación rechazada en '+nuevo)
  ok(pedir('PUT',f'/ordenes/{oid}/preparacion',{'esperado':'PENDIENTE','siguiente':'EN_PREPARACION'})[0]==409,'transición obsoleta rechazada')
  ok(db.sql(f"SELECT count(*) FROM factura_orden_producto f JOIN orden_producto o USING(id_orden_producto) WHERE o.id_orden='{oid}';")=='0','preparar no factura ni paga')
  login(*personal['cajero'][:2])
  code,factura=pedir('POST',f'/cobros/ordenes/{oid}/facturas',{'idCaja':caja,'cliente':'Prueba temporal cliente'});ok(code==201,'factura mediante sesión cajero');fid=factura['idFactura'];facturas.append(fid)
  ok(pedir('POST',f'/cobros/ordenes/{oid}/facturas',{'idCaja':caja})[0]==409,'facturación duplicada rechazada')
  code,factura=pedir('GET',f'/cobros/facturas/{fid}');ok(code==200 and all(p['precio']==4 and p['precioBase']==5 and p['descuento']==1 and '12 oz' in p['caracteristicas'] and 'Prueba temporal camarero' in p['atendidoPor'] and p['fechaOrden'] for p in factura['productos']) and factura['fechaFacturacion'],'factura conserva fechas camarero características e importes')
  code,res=pedir('POST',f'/cobros/facturas/{fid}/abonos',{'monto':3,'tipoPago':'TARJETA','referenciaExterna':'PRUEBA_TEMPORAL'});ok(code==201 and res['saldo']==5 and not res['completado'],'abono parcial tarjeta')
  code,res=pedir('POST',f'/cobros/facturas/{fid}/abonos',{'monto':5,'tipoPago':'EFECTIVO','efectivoRecibido':10});ok(code==201 and res['saldo']==0 and res['completado'],'efectivo completa saldo')
  code,abonos=pedir('GET',f'/cobros/facturas/{fid}/abonos');ok(code==200 and any(a.get('cambioEntregado')==5 and a.get('efectivoRecibido')==10 for a in abonos),'cambio persistente recuperado')
  ok(pedir('DELETE','/sesion')[0]==204 and pedir('GET','/sesion')[0]==401,'cierre de sesión real')
  print(f'{n} comprobaciones completas aprobadas')
 finally:
  # Elimina exclusivamente registros hijos de órdenes y facturas propias.
  for fid in facturas:
   db.sql(f"DELETE FROM pago_detalle WHERE id_pago IN (SELECT id_pago FROM pago WHERE id_factura='{fid}'); DELETE FROM pago WHERE id_factura='{fid}'; DELETE FROM factura_orden_producto WHERE id_factura='{fid}'; DELETE FROM factura WHERE id_factura='{fid}';")
  for oid in ordenes:db.sql(f"DELETE FROM orden_producto WHERE id_orden='{oid}'; DELETE FROM orden WHERE id_orden='{oid}';")
  for t,i in reversed(filas):db.sql(f"DELETE FROM {t} WHERE id_{t}='{i}';")
  print('Limpieza de datos temporales completada')
if __name__=='__main__':main()
