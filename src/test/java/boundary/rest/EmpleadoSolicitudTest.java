package boundary.rest;

import boundary.rest.dto.EmpleadoSolicitud;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas estrictas del JSON de Empleado, sin conectarse a PostgreSQL. */
class EmpleadoSolicitudTest {

    private static void invalida(String json, int estadoEsperado) {
        EmpleadoSolicitud.DatoInvalido ex = assertThrows(
                EmpleadoSolicitud.DatoInvalido.class,
                () -> EmpleadoSolicitud.leer(json));
        assertEquals(estadoEsperado, ex.getEstadoHttp());
    }

    private static String correcto() {
        return "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":true}";
    }

    @Test void cuerpoNuloVacioMalformadoOArregloRetorna400() {
        invalida(null, 400);
        invalida("", 400);
        invalida("   ", 400);
        invalida("{", 400);
        invalida("[]", 400);
        invalida("false", 400);
    }

    @Test void nombreEsObligatorioYConLongitudValida() {
        invalida("{\"apellido\":\"Pérez\",\"activo\":true}", 422);
        invalida("{\"nombre\":null,\"apellido\":\"Pérez\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"  \",\"apellido\":\"Pérez\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"A\",\"apellido\":\"Pérez\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"" + "x".repeat(256)
                + "\",\"apellido\":\"Pérez\",\"activo\":true}", 422);
    }

    @Test void apellidoEsObligatorioYConLongitudValida() {
        invalida("{\"nombre\":\"Ana\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":null,\"activo\":true}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"P\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"" + "a".repeat(256)
                + "\",\"activo\":true}", 422);
    }

    @Test void tiposIncorrectosParaNombreOApellidoRetornan400() {
        invalida("{\"nombre\":12,\"apellido\":\"Pérez\",\"activo\":true}", 400);
        invalida("{\"nombre\":\"Ana\",\"apellido\":false,\"activo\":true}", 400);
        invalida("{\"nombre\":[],\"apellido\":\"Pérez\",\"activo\":true}", 400);
    }

    @Test void activoEsObligatorioYUnBooleanoReal() {
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\"}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":null}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":\"true\"}", 400);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":1}", 400);
    }

    @Test void rechazaUuidYRelacionesEnviadasPorCliente() {
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":true,\"idEmpleado\":\"x\"}", 400);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":true,\"empleadoRolCollection\":[]}", 400);
    }

    @Test void validaComentariosYControlDeCaracteres() {
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":true,\"comentarios\":123}", 400);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":true,\"comentarios\":\"" + "x".repeat(1001) + "\"}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"activo\":true,\"comentarios\":\"x\\u0000y\"}", 422);
        invalida("{\"nombre\":\"An\\u0000a\",\"apellido\":\"Pérez\",\"activo\":true}", 422);
    }

    @Test void espaciosUnicodeYCaracteresControlInvalidos() {
        invalida("{\"nombre\":\"\\u200B\",\"apellido\":\"Pérez\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"\\u200B\",\"activo\":true}", 422);
        invalida("{\"nombre\":\"Ana\",\"apellido\":\"Pe\\u0007rez\",\"activo\":true}", 422);
    }

    @Test void aceptaEmpleadoValidoNormalizandoLosTextos() {
        EmpleadoSolicitud s = EmpleadoSolicitud.leer("{\"nombre\":\"  Ana  \","
                + "\"apellido\":\"  Pérez  \",\"activo\":true,"
                + "\"comentarios\":\"  Turno mañana  \"}");
        assertEquals("Ana", s.getNombre());
        assertEquals("Pérez", s.getApellido());
        assertTrue(s.getActivo());
        assertEquals("Turno mañana", s.getComentarios());
    }

    @Test void aceptaEmpleadoInactivoYComentariosOpcionales() {
        EmpleadoSolicitud s = EmpleadoSolicitud.leer(
                "{\"nombre\":\"María\",\"apellido\":\"López\",\"activo\":false,\"comentarios\":\"  \"}");
        assertFalse(s.getActivo());
        assertNull(s.getComentarios());
        EmpleadoSolicitud otra = EmpleadoSolicitud.leer(correcto());
        assertTrue(otra.getActivo());
        assertNull(otra.getComentarios());
    }
}
