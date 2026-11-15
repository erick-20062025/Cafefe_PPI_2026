# CORS de desarrollo — 15 de noviembre de 2026

Se corrigió `src/main/liberty/config/server.xml` usando el elemento nativo `cors` de Open Liberty:
- Alcance: `/Cafefe_PPI_2026-1.0-SNAPSHOT/api/`.
- Único origen: `http://127.0.0.1:5501`.
- Métodos: GET, POST, PUT, DELETE, OPTIONS (operaciones REST existentes).
- Cabeceras permitidas: Content-Type, Accept; sin comodines.
- `allowCredentials=true`; preflight sin caché (`maxAge=0`) para evitar permisos antiguos durante desarrollo.

No cambia autenticación, autorización, cookies, reglas DAO ni pantallas. La cabecera de credenciales prepara CORS, pero no crea una sesión. El futuro login debe acordar por separado cookies y transporte: localhost y 127.0.0.1 no son el mismo sitio para SameSite; esta corrección no cambia esa política.

Referencia oficial: https://openliberty.io/docs/latest/reference/config/cors.html

## Aplicación y resultados
Antes: GET tipos-producto devolvía 200 sin Access-Control-Allow-Origin.
Después: GET tipos-producto y cajas devuelven 200 con origen exacto y Access-Control-Allow-Credentials: true. OPTIONS POST JSON devuelve 200 con métodos y Content-Type permitidos. Otros orígenes, incluido localhost:5501, 127.0.0.1:5502 y null, no reciben permiso CORS. Un GET no autorizado puede seguir devolviendo 200: el navegador no permite leerlo; CORS no sustituye autorización del servidor.

La configuración se recargó en el Liberty activo. La fuente versionada es src/main/liberty/config/server.xml; se sincronizó solo el bloque cors con target/liberty/wlp/usr/servers/defaultServer/server.xml (archivo generado, fuera del commit). El servidor confirmó actualización de configuración. `mvn liberty:configure` no existe en la versión 3.10.3 del plugin; no usar ese comando. En `mvn liberty:dev` la fuente se sincroniza automáticamente; al volver a crear/iniciar el servidor con el plugin se debe comprobar que el server.xml efectivo contenga este bloque.

Pruebas HTTP reproducibles (solo GET y OPTIONS, sin escrituras a PostgreSQL):
```bash
cd /home/diegoli/Desktop/Cafefe_PPI_2026
python3 test/integracion/cors_http.py
```
Incluyen OPTIONS para la ruta real de Pedido `ordenes/publicas`. La variable opcional CAFEFE_API permite indicar otra URL de instancia para la prueba, sin cambiar el origen autorizado.

Comprobación puntual:
```bash
curl -i -H 'Origin: http://127.0.0.1:5501' \
  http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/tipos-producto
curl -i -X OPTIONS \
  -H 'Origin: http://127.0.0.1:5501' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type' \
  http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/ordenes/publicas
```

Navegador disponible: se cargaron las cinco categorías reales (bebida, Café caliente, Café helado, Postres, Comida), con CSS e imágenes HTTP, sin errores/advertencias en consola. No se creó ningún pedido. Al principio había un shell anterior del service worker; cerrar su pestaña y volver a abrir permitió activar la versión ya publicada por el frontend. No se modificó su worker.

## Error independiente file:///
No se reprodujo en el navegador integrado. Búsqueda en fuentes y recursos del frontend: ninguna referencia literal file:. `pedido_publico.js` resuelve su CSS y carpeta de imágenes con import.meta.url; al servirlo por HTTP, se observaron URLs http://127.0.0.1:5501/src/recursos/… . `PedidoPublicoService.urlImagen` rechaza protocolos distintos de HTTP(S) para URLs del servidor, incluyendo file:.

No se identificó un recurso concreto que provoque el error de Firefox: falta su línea completa y URL iniciadora, solicitadas al usuario. Abrir index.html directamente desde el explorador produciría URLs file: y origen null; es una hipótesis, no una causa confirmada. No se autorizó null ni se relajaron las protecciones del navegador.

Para verificar en Firefox: abrir http://127.0.0.1:5501/index.html#/pedido (no el archivo local), abrir Red/Consola, recargar y comprobar tipos-producto y sus cabeceras. Si reaparece file:///, copiar URL bloqueada y archivo/línea iniciadora. Si aparece el shell viejo, cerrar las pestañas del origen para activar el worker actualizado. El frontend debe seguir usando exactamente el puerto 5501.

Validación final: 6/6 pruebas HTTP pasan. `mvn test -q` finalizó con código 0; resumen Surefire: {'tests': 1004, 'failures': 0, 'errors': 0, 'skipped': 0}.
