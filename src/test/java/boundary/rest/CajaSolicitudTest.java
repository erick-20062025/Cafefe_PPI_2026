package boundary.rest;

import boundary.rest.dto.CajaSolicitud;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de JSON antes de acceder a CajaDAO. */
class CajaSolicitudTest {

    private static void invalida(String json, int esperado) {
        CajaSolicitud.DatoInvalido ex = assertThrows(CajaSolicitud.DatoInvalido.class,
                () -> CajaSolicitud.leer(json));
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
        invalida("{\"nombre\":\"a\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"" + "x".repeat(156) + "\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"\\u200B\",\"activo\":true}", 422);
    }

    @Test void rechazaNombreNoStringYControles() {
        invalida("{\"nombre\":123,\"activo\":true}", 400);
        invalida("{\"nombre\":false,\"activo\":true}", 400);
        invalida("{\"nombre\":\"C\\u0000aja\",\"activo\":true}", 422);
    }

    @Test void activoExigeBooleanoReal() {
        invalida("{\"nombre\":\"Caja A\"}", 422);
        invalida("{\"nombre\":\"Caja A\",\"activo\":null}", 422);
        invalida("{\"nombre\":\"Caja A\",\"activo\":\"true\"}", 400);
        invalida("{\"nombre\":\"Caja A\",\"activo\":1}", 400);
    }

    @Test void rechazaCamposInesperadosYManipulacionDeId() {
        invalida("{\"nombre\":\"Caja A\",\"activo\":true,\"idCaja\":\"hack\"}", 400);
        invalida("{\"nombre\":\"Caja A\",\"activo\":true,\"facturaCollection\":[]}", 400);
    }

    @Test void validaObservacionesOpcionales() {
        invalida("{\"nombre\":\"Caja A\",\"activo\":true,\"observaciones\":99}", 400);
        invalida("{\"nombre\":\"Caja A\",\"activo\":true,\"observaciones\":\""
                + "x".repeat(1001) + "\"}", 422);
        invalida("{\"nombre\":\"Caja A\",\"activo\":true,\"observaciones\":\"x\\u0000y\"}", 422);
    }

    @Test void aceptaCajaActivaYNormalizaTexto() {
        CajaSolicitud solicitud = CajaSolicitud.leer(
                "{\"nombre\":\"  Caja 1  \",\"activo\":true,\"observaciones\":\"  principal  \"}");
        assertEquals("Caja 1", solicitud.getNombre());
        assertTrue(solicitud.getActivo());
        assertEquals("principal", solicitud.getObservaciones());
    }

    @Test void aceptaCajaInactivaYSinObservaciones() {
        CajaSolicitud solicitud = CajaSolicitud.leer(
                "{\"nombre\":\"Caja B\",\"activo\":false,\"observaciones\":\"  \"}");
        assertEquals("Caja B", solicitud.getNombre());
        assertFalse(solicitud.getActivo());
        assertNull(solicitud.getObservaciones());
    }
}
