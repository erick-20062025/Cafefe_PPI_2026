# Preparación técnica del tercer parcial — 23 de noviembre de 2026

## Alcance y estado

Esta fase prepara implementación, imágenes, comandos y entrega preliminar. No certifica el recorrido integrado ni publica. Base inicial revisada: backend d0852fd y frontend e6f0c11; ramas nuevas feature/tercer-parcial-backend y feature/tercer-parcial-frontend. Se conservó nb-configuration.xml ajeno, sin incluirlo en commits. PostgreSQL habitual, pgAdmin, los contenedores de otros proyectos y red-cafefe permanecen intactos.

El PDF presenta 50%/50% en los títulos de contenedores y 45%/45% más 10% en su tabla final. Se usa la rúbrica detallada para organizar la revisión. Ambos Dockerfiles comienzan literalmente con FROM debian:stable; no se utiliza una imagen final de Liberty o Nginx.

## Auditoría backend

Inventario de 132 clases Java: entidades, DAO/controles, recursos REST, DTO y modelos JSF. La revisión se apoya en la auditoría de arquitectura existente y vuelve a contrastar configuración, dependencias, rutas REST y consultas principales. No equivale a una prueba exhaustiva de todas las ramas de código.

| Área | Evidencia y resultado |
|---|---|
| Entidades JPA | Las 19 @Table corresponden al esquema oficial. Comparación estática de nombres @Column/@JoinColumn sin columnas adicionales; precio_sugerido autorizado. No se deduce de esto el estado de la base habitual, que no se consultó. |
| JPA / pool | persistence.xml tiene DDL none en Jakarta y EclipseLink. PG_USER/PASS/DB/HOST/PORT permanecen externos. No se ejecutan migraciones al arrancar. |
| Orden / Recepción | Cliente en observaciones; RecepcionDAO agrega importes oficiales y abonos antes de unir detalles. Sin columnas de preparación/cancelación. Mantener la consulta antigua findPendientesPago como deuda hasta acordar consumidores. |
| Factura / Pago | DAO transaccionales, bloqueos y validaciones existentes conservados. Importe de detalles y abonos oficiales; no reconstrucción histórica con precio actual. |
| REST / DTO | Contratos existentes conservados. Sesión y Cobros tienen controles propios; las rutas heredadas /facturas y /pagos ahora exigen cajero; empleados/roles/asignaciones exigen administrador activo. |
| Maven | API EE11 preliminar desalineada con webProfile10 corregida a EE10. PrimeFaces provided y copiado explícitamente al servidor. Pruebas continúan con alcance test. JDBC pertenece al servidor. |
| Runtime limpio | Configuración Docker separada de la local, sin rutas personales ni server.env privado. Contexto /cafefe; WAR externo; permisos de usuario; JAVA_HOME explícito y clave interna LTPA generada al ejecutar. |
| Organización / POO | DefaultDAO/DAOInterface y modelos base reutilizan comportamiento real. DTO, boundary y control existen, pero OrdenResource, OrdenDAO, FacturaModel y algunos DAO acumulan responsabilidades. No se hizo una reescritura de negocio para desplegar. |
| Obsoletos | db/migrations contiene scripts históricos no oficiales. No se copian a imágenes ni ZIP y no deben ejecutarse; se conservan como evidencia, no como procedimiento vigente. No se elimina JSF suponiendo que es código muerto. |

## Auditoría frontend

Se revisaron boundary, control, entity, configuración, recursos, manifiesto, worker y pruebas. Web Components y JavaScript nativo se conservan.

| Área | Resultado |
|---|---|
| src autónomo | Corregido: index, manifiesto y worker estaban fuera. Ahora el profesor monta únicamente src; todos los imports y recursos del shell se verifican. |
| Servicios | URL centralizada. Desarrollo mantiene API local. Nginx suministra el mismo módulo de configuración con origen relativo; no hay puertos temporales incrustados. |
| Sesión | credentials include/no-store en servicio de sesión, navegación por roles. No se guardan claves en almacenamiento o SW. Origin exacto configurable en backend, sin alterar autenticación para el proxy. |
| Pedido / carrito | Confirmación, limpieza, timer, invalidación de respuestas y bloqueo de envío repetido conservados y cubiertos por pruebas. No hay idempotencia durable ante respuesta perdida. |
| Recepción | Controlador y estado local separado de vista; preparación solo en memoria y explícitamente no compartida. Se conserva nombre del cliente. |
| Cobros | Listas publican estado y versiones descartan respuestas tardías. Vista grande y bloqueo global de interacción siguen como deuda; no impiden montaje Docker. |
| Reactividad | Eventos de conexión, carrito, sesión, navegación y preparación conservados. No se sustituyó async/await ni se agregaron frameworks. Persisten grupos Promise.all previos; no se añadieron en este trabajo. |
| PWA | Shell v28 por alcance, API excluida, config por red primero, actualización del worker sin borrar datos. Worker antiguo solo retira su propio shell. |
| CSS / imágenes | Rutas de logos ajustadas al nuevo Document Root; no rediseño. Recursos incluidos exclusivamente dentro de src. |

## Archivos y motivo

Backend: pom.xml (API/dependencias), server.xml local (bibliotecas provided), SesionResource (origen configurable por entorno), RecepcionFilter (autorización REST), VistasInternasFilter (evitar evasión por JSF), plantilla JSF (retirar entradas de personal), pruebas de permisos, extras/probar_cobros_esquema_oficial.py (biblioteca externa en ensayo anterior), Dockerfile.backend y docker/backend (instalación y ejecución), .dockerignore (contexto mínimo), scripts/pruebas-docker.py (recursos aislados), scripts/generar-entrega.py (ZIP), scripts/test_preparacion.py (rechazos de empaquetado), docker/integrantes.template.txt y documentación.

Frontend: Dockerfile.frontend, docker/frontend y .dockerignore; src/index.html, src/manifest.webmanifest, src/sw.js; compatibilidad en index/manifiesto/worker raíz; pwa.js y rutas de logo; pruebas de registro, shell, banner y nuevo document-root; docs/DESPLIEGUE-FRONTEND.md. La etiqueta del pie dejó de indicar segundo entregable.

## Uso y comunicación

Ver docs/DESPLIEGUE-BACKEND.md y el documento equivalente del frontend. Navegador → Nginx /cafefe/api/ → cafefe.backend:9080/cafefe/api/ → PostgreSQL mediante PG_*. La red es ppi115net. El navegador no necesita DNS Docker. Cookies mantienen /cafefe, HttpOnly, SameSite Lax y caducidad de 30 minutos. El proxy conserva Origin, no lo suplanta.

Los comandos docker run independientes están documentados; no se depende de Compose. Para pruebas se preparó scripts/pruebas-docker.py: construir, preparar, red, iniciar, logs, comprobar, detener, limpiar. Con la autorización posterior de verificar permisos y esquema se ejecutó iniciar sobre PostgreSQL desechable; el ensayo de proxy y permisos no crea pedidos ni pagos. El recorrido HTTP completo se ejecutó además con el entorno aislado de extras. La prueba integrada visual y publicación siguen reservadas para la siguiente fase.

## Publicación pendiente

Nombres locales: cafefe-backend:ppi115-tercer-parcial y cafefe-frontend:ppi115-tercer-parcial. Las imágenes se construyen localmente; no están publicadas. Antes de publicar: cuenta Docker Hub real, matriz de permisos, pruebas completas y aprobación. Ejecutar después de esa aprobación (sustituir USUARIO por la cuenta real):

```sh
docker tag cafefe-backend:ppi115-tercer-parcial USUARIO/cafefe-backend:ppi115-tercer-parcial
docker tag cafefe-frontend:ppi115-tercer-parcial USUARIO/cafefe-frontend:ppi115-tercer-parcial
docker push USUARIO/cafefe-backend:ppi115-tercer-parcial
docker push USUARIO/cafefe-frontend:ppi115-tercer-parcial
docker pull USUARIO/cafefe-backend:ppi115-tercer-parcial
docker pull USUARIO/cafefe-frontend:ppi115-tercer-parcial
```

No se ejecutaron esos comandos. Revisar actualizaciones de seguridad de Java, Debian, Nginx, JDBC, PrimeFaces y Liberty antes de fijar los digests finales. La imagen usa el runtime Liberty completo para instalar todas las features sin depender de un runtime de la computadora; ocupa más que una imagen mínima. Reducirlo después es opcional, no debe desplazar pruebas obligatorias.

## Empaquetado preliminar

```sh
mvn package
python3 scripts/test_preparacion.py
python3 scripts/generar-entrega.py
unzip -l target/entrega/entrega-cafefe-preliminar.zip
```

Produce exactamente cafefe.war, frontend-src.zip e integrantes.txt. El ZIP interno solo contiene src. Lee los commits reales y advierte si hay cambios locales; nunca inventa identificadores. Los campos de nombres completos, carnets y Docker Hub siguen pendientes. La plantilla es preliminar deliberadamente; después de la prueba integrada se completará y revisará para retirar esa advertencia antes de generar la versión definitiva. La herramienta no compila por sí sola: ejecutar mvn package inmediatamente antes para no entregar un WAR anterior.

## Reparto

| Responsable | Trabajo concreto | Evidencia esperada |
|---|---|---|
| Diego | Construir ambas imágenes, supervisar consumo, ejecutar entorno desechable y prueba integrada | Logs, versiones/digests, resultados y capturas |
| Diego | Revisar UI, aportar Docker Hub y datos personales, autorizar publicación y ZIP final | Cuenta real, revisión de ticket/PWA, autorización |
| Erick | Revisar REST/DAO y permisos heredados, contrastar reglas HU1-HU4 | Lista de endpoints/roles y observaciones concretas |
| Erick | Revisar pom provided, server.xml, PG_*, WAR externo y comandos del PDF | Lista de cotejo documental; no levantar toda la pila |
| Erick | Probar por navegador/HTTP un entorno autorizado accesible preparado por Diego | Casos funcionales y errores reproducibles, sin pruebas de escritura en base habitual |
| Ambos | Completar integrantes, revisar commits y resultado final del ZIP | TXT definitivo revisado por los dos |

## Pendientes por prioridad

**Crítico antes de publicación:** prueba integrada Docker/navegador y revisión de las operaciones internas restantes de OrdenResource y escrituras de catálogo, cuyas políticas de camarero/administración necesitan acotarse antes de exponer toda la API a una red pública. No se presenta la autorización de /facturas y /pagos como cierre de toda la superficie administrativa. No hay certificación final.

Con autorización posterior de Diego, se corrigieron /facturas y /pagos para cajero exclusivo; empleados/roles/asignaciones requieren administrador. Se protegen también vistas JSF heredadas: Factura/Pago para cajero, Orden para camarero o cajero, otras páginas administrativas para administrador; recursos estáticos siguen accesibles. Se retiraron Empleados/Roles del menú JSF. La SPA no tenía esas opciones. No se crearon roles ni usuarios administrativos: el SQL oficial define rol.nombre como texto, sin enumeración ni filas predefinidas, y el código ya reconocía administrador. La base desechable no habilita administrador; cualquier sesión ordinaria recibe 403. La base habitual no se consultó para inferir si tiene alguno.

**Importante:** confirmar asignación virtual de la base del evaluador sin crearla automáticamente; completar integrantes y Docker Hub; verificar entrega descargando imágenes una vez publicadas; revisar HTTPS/Secure si se accede desde otra computadora. El acceso remoto de Erick debe habilitarse deliberadamente sobre pruebas aisladas, no exponer la base habitual.

**Opcional:** reducir tamaño del runtime, dividir vistas/DAO grandes, mejorar carga progresiva y revisar comportamiento ante respuesta perdida. No añadir tablas para resolver extras; no restaurar cancelación ni inventar persistencia de preparación.

## Resultado de las comprobaciones ejecutadas

- Backend: `mvn package`, 1071 pruebas, cero fallos/errores. WAR sin JAR en WEB-INF/lib.
- Frontend: `npm test`, 105 pruebas, cero fallos. Incluye componentes y src autónomo.
- Empaquetado: cinco pruebas de estructura y rechazos de archivos privados, enlaces, JAR y DDL; aprobadas.
- Dos imágenes construidas. Debian 13 stable, Java 21 y Nginx comprobados. Liberty limpio despliega el WAR; recurso components.js de PrimeFaces devuelve 200; sesión anónima 401 y preflight exacto 200. Se corrigieron permisos de lectura, JAVA_HOME y generación de clave LTPA antes de validar el arranque.
- Integración HTTP con PostgreSQL desechable a partir de los tres SQL: pedido público → Recepción de barista/cocinero → factura → abonos → comprobante; importes históricos conservados; permisos anónimos/roles/administración/JSF y logout comprobados. Esquema antes/después idéntico, 19 tablas, sin las 12 columnas retiradas. El primer intento tomó un WAR anterior mientras terminaba Maven; se repitió con el WAR terminado. También se corrigió el lector de errores del ensayo para HTTP 405 sin JSON.
- Docker con PostgreSQL en tmpfs: proxy Nginx → Liberty → esquema oficial, catálogo anónimo, cookie HttpOnly/Lax y Path=/cafefe, recuperación/cierre, cajero autorizado, otros roles rechazados en Cobros, administración rechazada para todas las cuentas ordinarias y origen incorrecto rechazado. Ningún administrador creado. Sin cambios de esquema; este ensayo acotado no creó órdenes ni pagos.
- El entorno temporal se regeneró tras reiniciar la computadora: los contenedores detenidos se retiraron solo después de comprobar sus etiquetas. PostgreSQL habitual y pgAdmin permanecieron activos.

No ejecutado: recorrido visual completo bajo Docker, instalación/offline/reconexión de PWA en ese despliegue, impresión real del ticket, descarga desde Docker Hub y certificación final. Los ensayos previos no reemplazan esas pruebas.

## Registro Git local

Frontend: `feature/tercer-parcial-frontend`, commit `043bdf4` (src autónomo, Nginx y pruebas).
Backend: `feature/tercer-parcial-backend`, commits `19769e6` (imagen/configuración/dependencias) y `675d229` (permisos y ensayo oficial). La documentación y scripts se guardan en una etapa posterior; `git log` identifica ese commit y el generador de entrega usa HEAD real.
No hubo push ni merge. El único cambio personal que debe quedar fuera es nb-configuration.xml. Las imágenes y el ZIP no se incorporan a Git.
