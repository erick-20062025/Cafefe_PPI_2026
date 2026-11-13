package boundary.rest;

import boundary.rest.dto.ProductoSolicitud;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de JSON antes de acceder a ProductoDAO. */
class ProductoSolicitudTest {

    private static void invalida(String json, int esperado) {
        ProductoSolicitud.DatoInvalido ex = assertThrows(ProductoSolicitud.DatoInvalido.class,
                () -> ProductoSolicitud.leer(json));
        assertEquals(esperado, ex.getEstadoHttp());
    }

    @Test void rechazaCuerpoVacioYJsonMalformado() {
        invalida(null, 400);
        invalida("", 400);
        invalida("   ", 400);
        invalida("{", 400);
        invalida("[]", 400);
        invalida("true", 400);
    }

    @Test void validaNombreObligatorio() {
        invalida("{}", 422);
        invalida("{\"precioSugerido\":0,\"activo\":true}", 422);
        invalida("{\"nombre\":null,\"precioSugerido\":0,\"activo\":true}", 422);
        invalida("{\"nombre\":\"   \",\"precioSugerido\":0,\"activo\":true}", 422);
        invalida("{\"nombre\":\"" + "x".repeat(156) + "\",\"precioSugerido\":0,\"activo\":true}", 422);
        invalida("{\"nombre\":\"\\u200B\",\"precioSugerido\":0,\"activo\":true}", 422);
    }

    @Test void rechazaNombreNoStringYControles() {
        invalida("{\"nombre\":123,\"precioSugerido\":0,\"activo\":true}", 400);
        invalida("{\"nombre\":false,\"precioSugerido\":0,\"activo\":true}", 400);
        invalida("{\"nombre\":\"C\\u0000aja\",\"precioSugerido\":0,\"activo\":true}", 422);
    }

    @Test void activoExigeBooleanoReal() {
        invalida("{\"nombre\":\"Producto A\"}", 422);
        invalida("{\"nombre\":\"Producto A\",\"activo\":null}", 422);
        invalida("{\"nombre\":\"Producto A\",\"activo\":\"true\"}", 400);
        invalida("{\"nombre\":\"Producto A\",\"activo\":1}", 400);
    }

    @Test void rechazaCamposInesperadosYManipulacionDeId() {
        invalida("{\"nombre\":\"Producto A\",\"precioSugerido\":0,\"activo\":true,\"idProducto\":\"hack\"}", 400);
        invalida("{\"nombre\":\"Producto A\",\"precioSugerido\":0,\"activo\":true,\"facturaCollection\":[]}", 400);
    }

    @Test void validaComentariosOpcionales() {
        invalida("{\"nombre\":\"Producto A\",\"precioSugerido\":0,\"activo\":true,\"comentarios\":99}", 400);
        invalida("{\"nombre\":\"Producto A\",\"precioSugerido\":0,\"activo\":true,\"comentarios\":\""
                + "x".repeat(1001) + "\"}", 422);
        invalida("{\"nombre\":\"Producto A\",\"precioSugerido\":0,\"activo\":true,\"comentarios\":\"x\\u0000y\"}", 422);
    }

    @Test void aceptaProductoActivaYNormalizaTexto() {
        ProductoSolicitud solicitud = ProductoSolicitud.leer(
                "{\"nombre\":\"  Producto 1  \",\"precioSugerido\":0,\"activo\":true,\"comentarios\":\"  principal  \"}");
        assertEquals("Producto 1", solicitud.getNombre());
        assertTrue(solicitud.getActivo());
        assertEquals("principal", solicitud.getComentarios());
    }

    @Test void aceptaProductoInactivaYSinComentarios() {
        ProductoSolicitud solicitud = ProductoSolicitud.leer(
                "{\"nombre\":\"Producto B\",\"precioSugerido\":0,\"activo\":false,\"comentarios\":\"  \"}");
        assertEquals("Producto B", solicitud.getNombre());
        assertFalse(solicitud.getActivo());
        assertNull(solicitud.getComentarios());
    }

    @Test void precioExigeNumeroNoNegativoSinPerderPrecision() {
        String base = "{\"nombre\":\"A\",\"activo\":true";
        invalida(base + "}", 422);
        invalida(base + ",\"precioSugerido\":null}", 422);
        for (String valor : new String[]{"\"1.25\"", "true", "[]", "{}"}) {
            invalida(base + ",\"precioSugerido\":" + valor + "}", 400);
        }
        invalida(base + ",\"precioSugerido\":-0.01}", 422);
        assertEquals(new java.math.BigDecimal("123456789.123456789"),
                ProductoSolicitud.leer(base + ",\"precioSugerido\":123456789.123456789}").getPrecioSugerido());
        assertEquals(0, ProductoSolicitud.leer(base + ",\"precioSugerido\":0}").getPrecioSugerido().signum());
    }

    @Test void limitesExactosYComentariosOpcionales() {
        String json = "{\"nombre\":\"" + "x".repeat(155)
                + "\",\"activo\":false,\"precioSugerido\":0,\"comentarios\":\""
                + "x".repeat(1000) + "\"}";
        assertEquals(1000, ProductoSolicitud.leer(json).getComentarios().length());
        assertEquals(155, ProductoSolicitud.leer(json).getNombre().length());
        assertNotNull(ProductoSolicitud.leer(json + " ".repeat(8192 - json.length())));
        invalida(json + " ".repeat(8193 - json.length()), 400);
        assertNull(ProductoSolicitud.leer("{\"nombre\":\"A\",\"activo\":true,\"precioSugerido\":0}").getComentarios());
        assertNull(ProductoSolicitud.leer("{\"nombre\":\"A\",\"activo\":true,\"precioSugerido\":0,\"comentarios\":null}").getComentarios());
    }
}
