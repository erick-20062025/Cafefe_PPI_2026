package boundary.rest;

import boundary.rest.dto.RolSolicitud;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de JSON antes de acceder a RolDAO. */
class RolSolicitudTest {

    private static void invalida(String json, int esperado) {
        RolSolicitud.DatoInvalido ex = assertThrows(RolSolicitud.DatoInvalido.class,
                () -> RolSolicitud.leer(json));
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
        invalida("{\"nombre\":\"R\\u0000ol\",\"activo\":true}", 422);
    }

    @Test void activoExigeBooleanoReal() {
        invalida("{\"nombre\":\"Rol A\"}", 422);
        invalida("{\"nombre\":\"Rol A\",\"activo\":null}", 422);
        invalida("{\"nombre\":\"Rol A\",\"activo\":\"true\"}", 400);
        invalida("{\"nombre\":\"Rol A\",\"activo\":1}", 400);
    }

    @Test void rechazaCamposInesperadosYManipulacionDeId() {
        invalida("{\"nombre\":\"Rol A\",\"activo\":true,\"idRol\":\"hack\"}", 400);
        invalida("{\"nombre\":\"Rol A\",\"activo\":true,\"empleadoRolCollection\":[]}", 400);
    }

    @Test void validaObservacionesOpcionales() {
        invalida("{\"nombre\":\"Rol A\",\"activo\":true,\"observaciones\":99}", 400);
        invalida("{\"nombre\":\"Rol A\",\"activo\":true,\"observaciones\":\""
                + "x".repeat(1001) + "\"}", 422);
        invalida("{\"nombre\":\"Rol A\",\"activo\":true,\"observaciones\":\"x\\u0000y\"}", 422);
    }

    @Test void aceptaRolActivoYNormalizaTexto() {
        RolSolicitud solicitud = RolSolicitud.leer(
                "{\"nombre\":\"  Rol 1  \",\"activo\":true,\"observaciones\":\"  principal  \"}");
        assertEquals("Rol 1", solicitud.getNombre());
        assertTrue(solicitud.getActivo());
        assertEquals("principal", solicitud.getObservaciones());
    }

    @Test void aceptaRolInactivoYSinObservaciones() {
        RolSolicitud solicitud = RolSolicitud.leer(
                "{\"nombre\":\"Rol B\",\"activo\":false,\"observaciones\":\"  \"}");
        assertEquals("Rol B", solicitud.getNombre());
        assertFalse(solicitud.getActivo());
        assertNull(solicitud.getObservaciones());
    }
}
