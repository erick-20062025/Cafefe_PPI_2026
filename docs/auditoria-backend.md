# Auditoría del backend y cobertura para el frontend

Base revisada: `origin/master` en `6c7e4c4` (PR #17). Las rutas de la matriz son relativas a `/api`. En las filas de catálogo, CRUD significa listar y crear en la ruta base, y consultar, reemplazar y eliminar en `/{id}`. Las listas completas permiten búsqueda y filtrado en cliente.

| Pantalla o flujo | Datos y consultas | Escrituras | Validaciones y estados para la interfaz |
|---|---|---|---|
| Caja | `GET /cajas` y detalle | CRUD `/cajas` | Nombre y activo. Borrado con facturas: 409. |
| Tipo de producto | `GET /tipos-producto` y detalle | CRUD `/tipos-producto` | Nombre y activo. Borrado de tipo asignado: 409. |
| Producto | `GET /productos` y detalle | CRUD `/productos` | Nombre, activo y precio sugerido. Borrado con relaciones: 409. |
| Producto · tipos | Catálogo `/tipos-producto` y `GET /producto-tipos-producto/productos/{id}`. Consulta inversa `/producto-tipos-producto/tipos-producto/{id}`. | `POST /producto-tipos-producto`, `DELETE /producto-tipos-producto/{idRelacion}` | Tipo activo, producto existente. Duplicado: 409. Sustituir asociación con baja y alta. |
| Tipo de característica | `GET /tipos-caracteristica` y detalle con expresión regular | CRUD `/tipos-caracteristica` | Nombre y activo, sintaxis de expresión validada por el DAO. Borrado con referencias: 409. |
| Característica | `GET /caracteristicas` y detalle, catálogo `/tipos-caracteristica` | CRUD `/caracteristicas` | Tipo existente y activo, nombre y estado. Borrado con asignaciones: 409. |
| Producto · características | Catálogos `/caracteristicas`, `/tipos-caracteristica`, y `GET /producto-caracteristicas/productos/{id}` | `POST /producto-caracteristicas`, `DELETE /producto-caracteristicas/{idRelacion}` | Característica activa, valor conforme al patrón Java, duplicado: 409. La validación del servidor prevalece. |
| Rol | `GET /roles` y detalle | CRUD `/roles` | Nombre y estado. Las referencias impiden el borrado. |
| Empleado | `GET /empleados` y detalle | CRUD `/empleados` | Campos personales y estado. Las asignaciones pueden impedir el borrado. |
| Empleado · roles | `GET /empleado-roles/empleados/{id}`, catálogos `/roles` y `/empleados` | `POST /empleado-roles`, `PUT` y `DELETE /empleado-roles/{idRelacion}` | Los padres deben estar activos al asignar. Duplicado y borrado con historial: 409. PUT modifica datos de la asignación. |
| Tipo de descuento | `GET /tipo-descuento`, detalle y `GET /tipo-descuento/{id}/descuentos` | CRUD `/tipo-descuento` | Porcentaje máximo y estado. POST/PUT mantienen la envoltura histórica. El listado asociado devuelve DTOs de descuento. |
| Descuento | `GET /descuentos` y detalle, catálogo `/tipo-descuento` | CRUD `/descuentos` | Tipo y vigencia. Al validar, usar el código y detalle de la respuesta. |
| Asignar descuento | `GET /descuento-productos/productos/{id}` y filtro inverso `/descuento-productos/descuentos/{id}` | `POST /descuento-productos`, `DELETE /descuento-productos/{idRelacion}` | Producto activo, vigencia y máximo del tipo. Duplicado: 409. |
| Orden · alta interna | `GET /productos`; combinar `/empleado-roles`, `/roles` y `/empleados` para camareros activos | `POST /ordenes` con filas | Precio del catálogo, cantidad válida y camarero activo. Fecha y camarero de cabecera inmutables tras alta. |
| Orden · pedido público | Catálogo según política de publicación | `POST /ordenes/publicas` | Nombre del cliente en las observaciones. Requiere `CAFEFE_CAMARERO_VIRTUAL_ID`; sin configuración devuelve 503. |
| Orden · consultar y editar filas | `GET /ordenes`, `GET /ordenes/{id}`, `GET /ordenes/{id}/productos` | `POST /ordenes/{id}/productos`, `DELETE /ordenes/{id}/productos/{idFila}` | Las filas nuevas toman el precio de catálogo. Si hay facturación, 409; fila ajena, 404. |
| Orden · borrar y aplicar promoción | Detalle de orden, `GET /ordenes/{id}/promociones` | `DELETE /ordenes/{id}`, `POST /ordenes/{id}/promociones/{idPromocion}` | Borrado con filas: 422 en el contrato actual. Una fila facturada impide alteraciones por promoción. |
| Factura · crear y editar | `GET /facturas` y detalle. Catálogos `/cajas`, `/empleado-roles`, `/ordenes`. `GET /ordenes/{id}/productos-facturables` para nuevas filas; el detalle de la factura devuelve el UUID de orden y el UUID y nombre del producto para las filas ya incluidas. | `POST /facturas`, `PUT /facturas/{id}` | Caja y asignación activas, una orden por factura, total positivo. Solo pendiente sin abonos para editar. Fila asignada a otra factura: 409. |
| Factura · eliminar | `GET /facturas/{id}` y resumen de Pago | `DELETE /facturas/{id}` | Solo pendiente y sin cabecera de pago, incluso anulada. El servidor determina el permiso final. |
| Pago · abonar | `GET /pagos/facturas/pendientes`, `GET /pagos/facturas/{id}/resumen`, `/productos` y `/abonos` bajo la misma factura | `POST /pagos/facturas/{id}/abonos` | Importe positivo dentro del saldo, método y referencia según medio. Pago parcial mantiene la factura pendiente y cubrir el total la marca pagada. |
| Pago · historial e impresión | `GET /pagos/facturas/pagadas`, resumen, productos y abonos | Impresión en el navegador a partir de los datos | La pantalla controla la impresión. El resumen permite comprobar una factura histórica pagada cuyo saldo no coincide. |

La ruta histórica `/ordenes/pendientes` conserva su semántica de pago. Para facturar, usar `productos-facturables`, que excluye filas de **cualquier** factura, incluso pendiente. Los detalles de Factura se editan con la cabecera y comparten el bloqueo con Pago.

## Límites de la verificación

- Maven: 1004 pruebas aprobadas, cero fallos, errores u omitidas.
- Open Liberty y PostgreSQL: 18 lecturas REST, 17 verificaciones del catálogo y Orden–Factura y 39 verificaciones del ciclo de Factura y Pago. Las 12 tablas de la integración y las 11 verificadas por el script de Factura recuperaron su contenido inicial.
- Regresión por scripts existentes: Caja 34, Rol 37, Empleado 50, EmpleadoRol 44, Descuento 51, DescuentoProducto 67, Orden (negativas) 22, Orden (positivas) 49, Pago 28, Producto 89, TipoProducto 66, ProductoTipoProducto 71, Característica 22 y ProductoCaracterística 22, sin fallos. Los conteos incluyen comprobaciones de limpieza cuando el script las realiza.
- Omisiones: el script de Orden no ejecutó el alta pública por falta de camarero virtual. El de Descuento no encontró un tipo inactivo. El script independiente de Pago omitió su alta positiva, que sí se comprobó con facturas temporales en la integración de Factura. La matriz se obtuvo del código de las vistas y no sustituye una pasada manual por todas las pantallas.
- No se encontró autenticación/autorización efectiva en el módulo REST, ni una política CORS de despliegue configurada. Antes de publicar hace falta decidir el proveedor de identidad, roles de acceso y orígenes del frontend. El catálogo y los endpoints administrativos no deben quedar a disposición anónima en Internet. La configuración de desarrollo contiene credenciales JDBC en el repositorio. Su migración y rotación requieren coordinación con el despliegue.
- El contrato histórico de TipoDescuento sigue aceptando entrada directamente mediante JSON-B y devuelve un mensaje en el alta. Cambiar su contrato requiere coordinar consumidores existentes. Los descuentos relacionados ya se proyectan a DTOs.
- Los bloqueos de exclusividad cubren las escrituras que pasan por los DAO. No sustituyen restricciones de unicidad frente a escrituras SQL externas. Las expresiones regulares de tipos son Java y requieren control de acceso administrativo en el despliegue.

## Limpieza de ramas

Todas las puntas inventariadas eran ancestros de `origin/master` en `6c7e4c4`. Conservar `master` y la rama de trabajo `feature/api-facturacion-caracteristicas` hasta integrar la auditoría. El `master` local estaba ocho commits atrás. El cambio local de NetBeans permanece en el árbol de trabajo.

Antes de borrar, volver a actualizar las referencias, verificar las puntas con `git merge-base --is-ancestor <rama> origin/master`, comprobar los worktrees y confirmar con el equipo si alguna rama tiene consumidores o PR abiertos. Actualizar el master local solo con avance rápido cuando se autorice la integración. Para ramas locales ya integradas, usar `git branch -d`, sin forzar. El borrado remoto requiere aprobación independiente. Cada punta puede recuperarse con `git branch <rama> <sha>` mientras la referencia siga alcanzable desde master.

Puntas locales candidatas:

| Rama | SHA |
|---|---|
| feature/CRUD_Factura | d7997d8 |
| feature/api-productos-facturacion | 63e73ae |
| feature/factura-seguridad | 51fd6ba |
| feature/validaciones-backend | 0e0bb45 |
| fix/validaciones-caracteristicas-relaciones | aae0c7c |
| fix/validaciones-empleado-rol | 2f0f72e |
| fix/validaciones-productos-orden | 95b89c3 |

Inventario remoto en la fecha de revisión:

```text
origin/correciones a0d20b97e3bb350e9e74575cc6d516017a99ca43
origin/feature/api-facturacion-caracteristicas 8f711443a4495a2d3546a4feea234dc9c3c80f0e
origin/feature/api-productos-facturacion 63e73ae305f38b76cf27b66b10339fb4b3021d98
origin/feature/descuento-producto f73fd21cb1d9f8154ceb73662e7f9e4773777bdd
origin/feature/orden e739c598e61eeba2867cbef6c5acab40082a1595
origin/feature/orden-descuentos 2902204536d5ec3cd6922cdc1b9527ba8c2283ca
origin/feature/rest-api 51fd6bae253416db41375a04a8fcfe7cfac55da2
origin/feature/validaciones-backend 0e0bb45394fdc558d116225ab576a28213e8e58a
origin/feature/validaciones-producto-descuento 0c903b9294945b2e877e4e28206039cc1793c6f2
origin/fix/empleado-rol-fk c69d9fdcf0275bb4ba7fb680877e36f14c847d96
origin/fix/validaciones-caracteristicas-relaciones aae0c7c85d98d2d2dac556e4cd8f7b323e380264
origin/fix/validaciones-empleado-rol 2f0f72e55163caa4682aebdf1d7a8906f8436c38
origin/fix/validaciones-productos-orden 95b89c3a66f7aa6045cdab3b59b2dde4aaee5edb
origin/master 6c7e4c4b91f050789ba1a789a4bc81b7926d6539
origin/test/pruebas-cobertura e05a750b1dc3a73dc8c386ec5168b7a0bc653d1d
```
