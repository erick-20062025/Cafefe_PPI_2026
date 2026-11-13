package boundary.rest;

import boundary.rest.dto.TipoProductoSolicitud;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de JSON antes de acceder a TipoProductoDAO. */
class TipoProductoSolicitudTest {

    private static void invalida(String json, int esperado) {
        TipoProductoSolicitud.DatoInvalido ex = assertThrows(TipoProductoSolicitud.DatoInvalido.class,
                () -> TipoProductoSolicitud.leer(json));
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
        invalida("{\"activo\":true}", 422);
        invalida("{\"nombre\":null,\"activo\":true}", 422);
        invalida("{\"nombre\":\"   \",\"activo\":true}", 422);
        invalida("{\"nombre\":\"" + "x".repeat(156) + "\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"\\u200B\",\"activo\":true}", 422);
    }

    @Test void rechazaNombreNoStringYControles() {
        invalida("{\"nombre\":123,\"activo\":true}", 400);
        invalida("{\"nombre\":false,\"activo\":true}", 400);
        invalida("{\"nombre\":\"C\\u0000aja\",\"activo\":true}", 422);
    }

    @Test void activoExigeBooleanoReal() {
        invalida("{\"nombre\":\"TipoProducto A\"}", 422);
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":null}", 422);
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":\"true\"}", 400);
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":1}", 400);
    }

    @Test void rechazaCamposInesperadosYManipulacionDeId() {
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":true,\"idTipoProducto\":\"hack\"}", 400);
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":true,\"productoTipoProductoCollection\":[]}", 400);
    }

    @Test void validaObservacionesOpcionales() {
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":true,\"observaciones\":99}", 400);
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":true,\"observaciones\":\""
                + "x".repeat(1001) + "\"}", 422);
        invalida("{\"nombre\":\"TipoProducto A\",\"activo\":true,\"observaciones\":\"x\\u0000y\"}", 422);
    }

    @Test void aceptaTipoProductoActivaYNormalizaTexto() {
        TipoProductoSolicitud solicitud = TipoProductoSolicitud.leer(
                "{\"nombre\":\"  TipoProducto 1  \",\"activo\":true,\"observaciones\":\"  principal  \"}");
        assertEquals("TipoProducto 1", solicitud.getNombre());
        assertTrue(solicitud.getActivo());
        assertEquals("principal", solicitud.getObservaciones());
    }

    @Test void aceptaTipoProductoInactivaYSinObservaciones() {
        TipoProductoSolicitud solicitud = TipoProductoSolicitud.leer(
                "{\"nombre\":\"TipoProducto B\",\"activo\":false,\"observaciones\":\"  \"}");
        assertEquals("TipoProducto B", solicitud.getNombre());
        assertFalse(solicitud.getActivo());
        assertNull(solicitud.getObservaciones());
    }

    @Test void aceptaUnCaracterYObservacionesNulasOAusentes() {
        assertEquals("A", TipoProductoSolicitud.leer("{\"nombre\":\" A \",\"activo\":true}").getNombre());
        assertNull(TipoProductoSolicitud.leer("{\"nombre\":\"A\",\"activo\":true}").getObservaciones());
        assertNull(TipoProductoSolicitud.leer("{\"nombre\":\"A\",\"activo\":false,\"observaciones\":null}").getObservaciones());
    }

    @Test void limitesExactosDeNombreObservacionesYCuerpo() {
        String json = "{\"nombre\":\"" + "x".repeat(155)
                + "\",\"activo\":true,\"observaciones\":\"" + "x".repeat(1000) + "\"}";
        assertEquals(155, TipoProductoSolicitud.leer(json).getNombre().length());
        assertEquals(1000, TipoProductoSolicitud.leer(json).getObservaciones().length());
        assertNotNull(TipoProductoSolicitud.leer(json + " ".repeat(8192 - json.length())));
        invalida(json + " ".repeat(8193 - json.length()), 400);
    }
}
