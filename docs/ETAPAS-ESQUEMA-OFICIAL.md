# Retoma: compatibilidad con el esquema oficial — 18 de noviembre de 2026

## Base y límites
Solo SQL original del docente + precio_sugerido + nueva PK de producto_tipo_producto.
Las pruebas usan un cluster PostgreSQL y un servidor Liberty temporales. No se conecta a la base habitual.
Sin commits, push ni merge. nb-configuration.xml conserva sus cambios previos.

## Etapa anterior: abonos (verificada)
PagoDetalle utiliza únicamente columnas oficiales. El efectivo recibido se valida en la petición y el cambio se muestra al cobrar; no hay historial persistente del cambio.
Prueba reproducible: `mvn package` y `python3 extras/probar_cobros_esquema_oficial.py`.

## Etapa 1: Orden, Recepción y facturación
- Retiradas las diez columnas restantes de Orden y OrdenProducto; no se borran datos ni columnas de la base habitual.
- Nombre del cliente y observaciones se conservan juntos en orden_producto.observaciones.
- Recepción consulta productos sin factura pagada, mediante las relaciones oficiales.
- Facturación no exige DESPACHADA; mantiene bloqueos e impide facturar dos veces una fila.
- Preparación y cancelación no se simulan: se retiraron sus controles y se rechazan sus operaciones.
- Retirados descuento automático y snapshots de promociones/características; se conservan los importes guardados y las tablas oficiales de descuentos. La promoción manual anterior basada en campos oficiales permanece.
- Ticket usa el precio de factura_orden_producto; no muestra un desglose histórico inventado.
- JPA declara explícitamente schema-generation=none y ddl-generation=none.

Verificaciones: suite Maven completa aprobada; 15 archivos de pruebas JS aprobados, incluidas nuevas pruebas de renderizado de Recepción.
Integración ampliada HU2→HU3→HU4 APROBADA: pedido anónimo, nombre y notas, ambos roles de Recepción, facturación, abonos parciales/finales, ticket pagado, salida de Recepción. Se modificó el catálogo únicamente en la base desechable para comprobar que los precios facturados permanecen intactos. El fixture inicialmente tenía producto inactivo; quedó corregido.

## Pendientes
Integración y configuración confirmadas en etapas 1 y 2. Quedan refactor amplio por módulos, revisión visual en navegador real y actualización de utilidades antiguas de extras que lean credenciales directamente de server.xml. No ejecutar esas utilidades contra la base habitual. Tercer entregable fuera del alcance.

## Etapa 2: configuración (verificada)
Pool Liberty: PG_HOST, PG_PORT, PG_DB, PG_USER, PG_PASS. Los valores locales previos se conservaron en server.env ignorado por Git, sin imprimirlos. Plantilla server.env.example sin credenciales reales.
Para otro entorno, definir las cinco variables o copiar/completar server.env.example como server.env local. Evitar definir valores contradictorios en ambos lugares.

Verificación: integración HU2→HU3→HU4 repetida con el server.xml real parametrizado, puertos y rutas temporales. Aprobada. URL del frontend centralizada en src/config/api.js, compartida por DefaultService y SesionService. PWA actualizada a v22 e incluye ese módulo. Pruebas JS aprobadas. Prueba de ticket renderizado aprobada (cliente, importes registrados, impresión, sin desglose inventado).

## Etapa 3: refactor acotado (verificado)
Separar el estado y las peticiones de las listas de Cobros de su renderizado. No se renombrarán todos los módulos en esta etapa.

ListadosCobros coordina las tres consultas independientes; cada respuesta publica su estado sin esperar otras. La vista solo renderiza carga, resultados, vacío o error. Las respuestas anteriores o de una vista desmontada se descartan. No usa Promise.all. Se agregó el controlador al service worker.
Pruebas: 16 archivos JS aprobados, incluidas respuestas desordenadas, error independiente y descarte al invalidar. 9 pruebas del componente Cobros aprobadas y 2 de renderizado de Recepción. Backend: 1036 pruebas, cero fallos (las pruebas de extras retirados se sustituyeron por rechazo sin escrituras).

## Retoma sugerida
1. Revisión visual real de Pedido, Recepción y ticket contra un despliegue de pruebas (las pruebas actuales de Web Components usan dobles DOM nativos).
2. Completar separación vista/controlador/servicio en apertura de orden y factura de Cobros; aún existen Promise.all antiguos en esos dos métodos, fuera del refactor de listas.
3. Revisar utilidades de integración antiguas y documentación de las migraciones históricas: las migraciones no se necesitan ni se ejecutan, pero siguen en Git como archivos previos.
4. Refactor de nombres y módulos del resto del frontend/backend, actualizando imports y PWA por etapas. Descuento automático permanece retirado; tablas oficiales y promoción manual anterior se conservan.

No hubo commits, push ni merge. La base habitual no se abrió ni se modificó. Los servicios de prueba se detienen y su directorio temporal se elimina al terminar.

## Cierre de verificación de esta ejecución
- Integración completa repetida con el pool PG_*: APROBADA.
- Comparación de todas las columnas oficiales, tipos, nulabilidad y valores por defecto antes/después de JPA: idéntica.
- Búsqueda en src/main/java: cero referencias a las 12 columnas extra.
- Frontend final: 16 archivos de pruebas aprobados; git diff --check limpio en ambos repositorios.
- Las credenciales locales quedaron solo en server.env ignorado. El server.xml versionable contiene referencias a PG_*.
- No se reinició ni desplegó sobre el servidor habitual. El WAR de target contiene la versión backend verificada.

## Continuación: Recepción, nombres y preparación local
- Revisados SQL oficial, ajustes, diffs previos y PDF segundo parcial: HU3 exige consultar órdenes no pagadas y sus productos para preparar/despachar; no especifica un campo persistente de preparación.
- Decisión: seguimiento local útil como ayuda de trabajo individual. Map encapsulado por vista, sin almacenamiento ni red; aviso explícito de pérdida al recargar/salir y ausencia de sincronización. No decide facturación ni pago.
- RecepcionDAO agrega facturas y pagos por separado en una consulta nativa; evita multiplicar abonos por producto. El estado de una factura compartida se aplica a sus filas solo al estar totalmente cubierta, sin adjudicar pagos a una orden arbitrariamente. Estado parcial indica abonos en facturas asociadas, no un importe distribuido por orden.
- DescripcionOrden centraliza el formato descriptivo de HU2 y la lectura del cliente; no hay petición por cada orden.
- AtencionOrden obtiene el camarero desde la orden; identifica Pedido web por el ID configurado de asignación virtual. Es una referencia actual, no una copia histórica; no se confunde con el cajero.
- Retirados endpoints y métodos de preparación persistente/cancelación, y calculador automático de descuentos sin uso. Promociones manuales oficiales conservadas.
- Separado recepcion-service.js del controlador y agregado preparacion-local.js. PWA v23 incluye ambos.

### Explicación del cambio anterior 1047 → 1036
CancelacionOrdenTest tenía 11 casos ejecutados (7 @Test y 4 valores parametrizados); se sustituyeron por un rechazo sin escritura: -10. PreparacionOrdenTest tenía 2 casos y quedó un rechazo: -1. Total -11. Ninguno correspondía al recorrido obligatorio de pago/consulta de HU3.
En esta continuación se eliminan esos dos tests de rechazo porque ya no existen esos endpoints/métodos, y los ocho casos del calculador de descuento automático retirado. Se agregan pruebas de cálculo real de pago, nombres, atención y preparación local. No se eliminan pruebas de login, pedido, facturación ni abonos.

Verificación pendiente: integración ampliada y navegador real en servidores temporales. La primera compilación de RecepcionDAO detectó el tipo raw de JPA y se corrigió declarando List<Object[]> explícitamente. Suite backend pasó después de esa corrección. Repetir suite tras limpieza del calculador automático.

## Etapa 20 de noviembre: conexión habitual y actualización PWA
- Causa reproducida: la prueba anterior sirvió su copia del frontend en el mismo origen 127.0.0.1:5501. El service worker conservó el módulo de configuración con el puerto temporal 34901, ya apagado. El archivo de configuración del proyecto sí apuntaba al servidor correcto.
- Backend habitual comprobado en 9080 con contexto /Cafefe_PPI_2026-1.0-SNAPSHOT/api/. /cafefe/api/ corresponde únicamente al despliegue desechable. GET sesión anónimo devuelve 401; catálogo devuelve 200; preflight JSON autoriza origen 127.0.0.1:5501 y credenciales. Origen ajeno sin Access-Control-Allow-Origin.
- PWA: configuración API consultada primero por red sin caché HTTP, con respaldo offline; registro updateViaCache:none, activación sin recargar formularios. Limpieza limitada al shell propio y versiones raíz antiguas: no borra datos del usuario ni cachés de otros alcances. Tras aviso de actualización, recargar al terminar la operación actual.
- Las pruebas --browser ahora usan otro puerto frontend aleatorio. Tanto CORS temporal como la validación de origen de sesión reciben ese único origen. SesionResource conserva por defecto el origen habitual; la propiedad JVM cafefe.frontend.origin se utiliza solo en el despliegue aislado. No se escriben puertos temporales en src/config/api.js del proyecto.
- Verificación real en navegador integrado (no en el perfil Brave): login con la cuenta existente autorizada, recuperación tras recargar, categorías reales, Recepción, Cobros y cierre de sesión. Listados vacíos cargan sin error; consola sin errores al finalizar. No se crearon pedidos, empleados, facturas ni pagos en la base habitual. Captura /tmp/cafefe-conexion-cobros.png.
- Backend: mvn -q package, 1034 pruebas, cero fallos/errores/omitidas. Integración en PostgreSQL y Liberty desechables aprobada: pedido público, ambos roles de Recepción, facturación, abonos y ticket; nombre/notas y precios históricos conservados. Las 19 tablas y sus columnas/tipos/nulabilidad/defaults permanecen iguales después de JPA.
- Frontend: 92 pruebas aprobadas, incluidas regresiones de configuración obsoleta, respaldo offline, limpieza por alcance, nombre y pago parcial en Recepción, eventos de preparación local y ocultación de controles durante carga/error.

## Etapa acotada: nombres de módulos de Pedido
- Renombrados boundary/pedido_publico.js a pedido-publico-view.js, control/pedido_publico_service.js a pedido-publico-service.js y entity/pedido_publico.js a pedido-publico-model.js. Actualizados imports de app, Cobros, vista y pruebas; precaché PWA v25. Sin cambios de reglas de negocio.
- Suite frontend completa repetida: 92 pruebas aprobadas. diff --check limpio en ambos repositorios.
- Pendiente: extracción del estado/controlador de Pedido (el modelo actual contiene funciones de reglas), consultas progresivas de productos y apertura de orden/factura en Cobros. No se incorporó Promise.all nuevo; los usos anteriores aún requieren ese refactor. Falta verificar impresión física/PDF y el perfil Brave del usuario. La preparación local es deliberadamente individual y se pierde al salir/recargar; no equivale a estado persistente compartido.
- Sin commits, push ni merge. nb-configuration.xml conserva el cambio local previo y no se editó. PG_* y esquema oficial preservados. Tercer entregable fuera de alcance.
- Verificación adicional del modo --browser: catálogo de prueba servido en un origen temporal distinto; el catálogo habitual en 5501 siguió mostrando sus categorías reales tras recargar con los módulos renombrados. Consola sin errores. Captura /tmp/cafefe-conexion-catalogo.png. Se solicitó STOP para limpiar los servicios desechables; el frontend habitual permanece servido en 5501.
