# Próxima fase: prueba integrada y publicación

No se ejecuta automáticamente como parte de preparar o empaquetar. Ninguna orden, factura ni abono de prueba se crea en la base habitual.

## Entorno desechable

Desde el backend:

```sh
python3 scripts/pruebas-docker.py construir
python3 scripts/pruebas-docker.py preparar --fuentes "$HOME/Downloads"
# El comando anterior informa una carpeta privada /tmp/cafefe-tercer-parcial-...
python3 scripts/pruebas-docker.py iniciar --entorno /tmp/cafefe-tercer-parcial-REEMPLAZAR
python3 scripts/pruebas-docker.py logs
python3 scripts/pruebas-docker.py comprobar
python3 scripts/verificar-permisos-docker.py --entorno /tmp/cafefe-tercer-parcial-REEMPLAZAR
```

El preparador lee los tres SQL oficiales, elimina solamente delimitadores Markdown y crea fixtures nuevos en archivos temporales. El inicio aplica esos SQL únicamente en `cafefe.db.pruebas` con tmpfs y sin puerto publicado. Usa postgres:16-bookworm como herramienta externa de pruebas, no como tercera imagen a entregar. Las dos imágenes de entrega sí parten de debian:stable. No conecta postgres-cafefe a otra red ni modifica red-cafefe.

Se publica frontend solo en `http://localhost:18080` y backend en `127.0.0.1:19080`. Las cuentas sintéticas están en `cuentas.json` de esa carpeta, con permisos 600; no publicarlas, adjuntarlas ni confundirlas con empleados reales. Los puertos 5501, 9080, 5432 y 8080 habituales se conservan. Los nombres cafefe.frontend/backend deben estar libres; el script se detiene si ya existen.

## Matriz a ejecutar juntos

| Área | Verificación y evidencia requerida |
|---|---|
| Imágenes | Construcción de ambas, /etc/os-release, Java 21, Liberty y nginx -t; guardar digests y versiones |
| Montajes | WAR externo con contexto /cafefe; src externo sin raíz del repo; solo lectura; archivo ausente causa fallo claro |
| Base | 19 tablas, columnas iguales a los SQL antes/después; sin las 12 columnas retiradas; pool PG_* correcto |
| Proxy | Network solo al origen :18080/cafefe/api/, DNS interno cafefe.backend; configuración API no cacheada por HTTP |
| Sesión | Nombre/UUID válido, incorrecto y empleado inactivo; recuperar; roles; cerrar; cookie HttpOnly/Lax y Path=/cafefe; sin credenciales persistidas |
| Origen | POST con Origin incorrecto debe dar 403; OPTIONS con origen permitido y Content-Type JSON; ningún wildcard |
| Pedido | Anónimo: categoría/producto, nombre, cantidades válidas/inválidas, total; confirmar una vez; doble clic; error conserva carrito; banner 5 s |
| Recepción | Barista y cocinero: orden sin factura, nombre/notas, productos; estado temporal se pierde al recargar y no se comparte; cajero solo no accede |
| Factura | Cajero: orden pendiente, facturar sin requisito de preparación; no doble factura; precios históricos inalterados |
| Abonos | Dos pagos parciales; rechazar cero/negativo/sobrepago; referencia; saldo correcto; efectivo y cambio no se guardan como historia |
| Comprobante | Completar pago; sale de pendientes; ticket, atendido por, impresión/PDF y cerrar/reabrir; no recalcular con precios actuales |
| PWA | Instalar en localhost; offline muestra aviso rojo grande, catálogo público, bloquea mutaciones; recuperar conexión; actualizar SW sin borrar datos |
| Fallos | Detener solo backend de pruebas: Nginx responde error, la UI informa; recrearlo y comprobar recuperación DNS y sesión expirada |
| Permisos heredados | Revisar matriz administrativa y rutas alternativas /facturas y /pagos antes de exposición pública |
| Entrega | ZIP con tres archivos, ZIP interno solo src, WAR sin JAR empaquetados, TXT completo y commits coincidentes |
| Docker Hub | Solo después de autorización: publicar, descargar desde un entorno limpio y repetir montajes/flujo |

Consultar logs acotados:

```sh
python3 scripts/pruebas-docker.py logs --contenedor cafefe.frontend
python3 scripts/pruebas-docker.py logs --contenedor cafefe.db.pruebas
python3 scripts/pruebas-docker.py detener
python3 scripts/pruebas-docker.py limpiar
```

Detener elimina únicamente los contenedores con nombres y etiqueta reconocidos; los datos de PostgreSQL tmpfs se pierden deliberadamente. Limpiar además elimina ppi115net solo si este script la creó y está vacía. Nunca se usan prune ni limpiezas globales. La carpeta privada temporal se conserva para evidencia; borrarla manualmente al cerrar la fase. Las imágenes no se borran.

El ensayo HTTP anterior `extras/probar_cobros_esquema_oficial.py` sigue disponible y se adaptó a PrimeFaces provided. No sustituye esta prueba Docker ni la prueba visual.
