# Backend del tercer parcial

La imagen parte de `debian:stable`; instala Java 21 de Debian y Open Liberty 26.0.0.11 desde Maven Central. PostgreSQL JDBC 42.7.3 y PrimeFaces 13.0.0 Jakarta se descargan con SHA-256 fijado en `docker/backend/descargar.sh`. Debian stable y los paquetes apt son referencias móviles: el procedimiento es repetible, pero no promete una imagen idéntica bit a bit. Guardar digest y versiones de las imágenes aprobadas antes de publicar.

## Dependencias y montaje

`pom.xml` compila con Jakarta EE 10, igual a `webProfile-10.0`. Jakarta y PrimeFaces son `provided`; las herramientas de pruebas conservan `test`. Liberty suministra Jakarta; la imagen suministra PrimeFaces mediante `privateLibraryRef` y JDBC mediante `libraryRef`. No se marcan las herramientas de pruebas como provided. No se empaquetan bibliotecas en WEB-INF/lib.

El WAR se monta **en `/opt/war/cafefe.war`**. Es una ruta propia documentada, equivalente al ejemplo de montaje del profesor; no tiene que ser la ruta ilustrativa de su computadora. `webApplication` fija `/cafefe`. Dropins se desactiva para no desplegar dos veces el mismo archivo. JAVA_HOME apunta al Java instalado por Debian. La clave de cifrado LTPA se genera aleatoriamente al ejecutar, con permisos 600, y se conserva dentro del mismo contenedor; no viaja en la imagen. Al recrear el contenedor se renueva, coherente con sesiones no persistentes.

El proceso se ejecuta como UID/GID 10001, con lectura de bibliotecas y escritura solo en su servidor y su home. El archivo montado debe ser legible por ese usuario (por ejemplo, WAR público con modo 644).

```sh
mvn package
docker build -f Dockerfile.backend -t cafefe-backend:ppi115-tercer-parcial .
docker network inspect ppi115net >/dev/null 2>&1 || docker network create ppi115net
docker run -d --name cafefe.backend -h cafefe.backend --network ppi115net \
  -p 127.0.0.1:9080:9080 --env-file /ruta/privada/backend.env \
  --mount "type=bind,src=$PWD/target/Cafefe_PPI_2026-1.0-SNAPSHOT.war,dst=/opt/war/cafefe.war,readonly" \
  cafefe-backend:ppi115-tercer-parcial
```

Los nombres deben estar libres. No reemplazar ni detener contenedores existentes para ejecutar este ejemplo. En desarrollo local, `mvn package` también copia JDBC y PrimeFaces a target/liberty para mantener el despliegue habitual. La configuración local conserva su contexto anterior y puerto 9080. No ejecutar `mvn clean` mientras Liberty local esté activo.

## Variables

| Variable | Significado |
|---|---|
| PG_USER / PG_PASS | Usuario y contraseña de la base de destino, aportados al ejecutar |
| PG_DB | Base existente con las 19 tablas oficiales |
| PG_HOST / PG_PORT | Host alcanzable desde el contenedor y puerto PostgreSQL |
| CAFEFE_FRONTEND_ORIGIN | Origen exacto del navegador; predeterminado `http://localhost`, sin barra final |
| CAFEFE_CAMARERO_VIRTUAL_ID | UUID de la asignación empleado_rol virtual activa, previamente autorizada |

Copiar `docker/backend/variables.example` fuera del repositorio, completar y aplicar `chmod 600`. No usar `localhost` como PG_HOST salvo que PostgreSQL esté en el mismo contenedor (no es este diseño). Un archivo env no debe usar comillas de shell alrededor de valores: Docker las conservaría.

No se crean usuarios, tablas, columnas ni asignaciones al arrancar. JPA mantiene ambas opciones DDL en `none`. Los scripts históricos de db/migrations no forman parte del despliegue ni de la entrega; no ejecutarlos sobre el esquema oficial.

## Sesiones y proxy

CAFEFE_FRONTEND_ORIGIN alimenta tanto CORS como la validación de Origin en las mutaciones. La propiedad JVM `cafefe.frontend.origin` permanece compatible con el entorno de pruebas anterior; no se establece en esta imagen. Se exige coincidencia exacta, nunca `*`. Cookie opaca HttpOnly, SameSite Lax, ruta /cafefe, 30 minutos por inactividad; el proxy conserva esa ruta. No confía en Origin inventado a partir de Host o cabeceras reenviadas. Para producción remota hace falta HTTPS y revisar cookie Secure; HTTP localhost es el entorno de evaluación preparado.

Healthcheck consulta categorías: sin base disponible puede quedar unhealthy aunque Liberty haya desplegado. Logs: `docker logs --tail 100 cafefe.backend`. No interpretar el estado running como prueba de conexión o de todos los flujos.

Referencias técnicas: [classloader y bibliotecas](https://openliberty.io/docs/latest/class-loader-library-config.html), [proxy HTTP de Nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html). Revalidar las versiones y alertas de seguridad antes de publicación; no se afirma haber realizado un escaneo de vulnerabilidades de imágenes.
