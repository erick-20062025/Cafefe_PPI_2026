package boundary.rest;

import boundary.rest.dto.EmpleadoRolSolicitud;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** JSON original: verifica campos, tipos y valores antes de acceder al DAO. */
class EmpleadoRolSolicitudTest {
    private static final UUID E = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID R = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static String crear() {
        return "{\"idEmpleado\":\""+E+"\",\"idRol\":\""+R+"\",\"activo\":true}";
    }
    private static void invalido(String json, boolean creacion, int estado) {
        EmpleadoRolSolicitud.DatoInvalido ex = assertThrows(
            EmpleadoRolSolicitud.DatoInvalido.class,
            () -> { if (creacion) EmpleadoRolSolicitud.crear(json);
                    else EmpleadoRolSolicitud.modificar(json); });
        assertEquals(estado, ex.getEstadoHttp());
    }

    @Test void rechazaCuerposSinJsonOIncorrectos() {
        invalido(null,true,400); invalido("",true,400); invalido("  ",false,400);
        invalido("{",true,400); invalido("[]",true,400); invalido("null",false,400);
    }
    @Test void crearExigeEmpleadoYRol() {
        invalido("{\"activo\":true}",true,422);
        invalido("{\"idEmpleado\":null,\"idRol\":\""+R+"\",\"activo\":true}",true,422);
        invalido("{\"idEmpleado\":\"\",\"idRol\":\""+R+"\",\"activo\":true}",true,422);
        invalido("{\"idEmpleado\":\""+E+"\",\"activo\":true}",true,422);
        invalido("{\"idEmpleado\":\""+E+"\",\"idRol\":\"  \",\"activo\":true}",true,422);
    }
    @Test void crearRechazaIdsDeTipoIncorrecto() {
        invalido("{\"idEmpleado\":12,\"idRol\":\""+R+"\",\"activo\":true}",true,400);
        invalido("{\"idEmpleado\":\""+E+"\",\"idRol\":true,\"activo\":true}",true,400);
        invalido("{\"idEmpleado\":\"mal-id\",\"idRol\":\""+R+"\",\"activo\":true}",true,400);
        invalido("{\"idEmpleado\":\""+E+"\",\"idRol\":\"invalido\",\"activo\":true}",true,400);
    }
    @Test void activoExigeBooleanoReal() {
        invalido("{\"idEmpleado\":\""+E+"\",\"idRol\":\""+R+"\"}",true,422);
        invalido("{\"activo\":null}",false,422);
        invalido("{\"activo\":\"true\"}",false,400);
        invalido("{\"activo\":1}",false,400);
    }
    @Test void rechazaCamposExtraYReasignacion() {
        invalido(crear().replace("}",",\"idEmpleadoRol\":\"x\"}"),true,400);
        invalido("{\"activo\":true,\"idRol\":\""+R+"\"}",false,400);
        invalido("{\"activo\":true,\"idEmpleado\":\""+E+"\"}",false,400);
        invalido("{\"activo\":true,\"ordenCollection\":[]}",false,400);
    }
    @Test void observacionesSeValidan() {
        invalido("{\"activo\":true,\"observaciones\":99}",false,400);
        invalido("{\"activo\":true,\"observaciones\":\""+"X".repeat(1001)+"\"}",false,422);
        invalido("{\"activo\":true,\"observaciones\":\"hola\\u0000mundo\"}",false,422);
    }
    @Test void aceptaCrearYNormalizaObservaciones() {
        EmpleadoRolSolicitud s = EmpleadoRolSolicitud.crear(
            crear().replace("}",",\"observaciones\":\"  prueba REST  \"}"));
        assertEquals(E,s.getIdEmpleado()); assertEquals(R,s.getIdRol());
        assertTrue(s.getActivo()); assertEquals("prueba REST",s.getObservaciones());
    }
    @Test void aceptaModificarDesactivarYObservacionesVacias() {
        EmpleadoRolSolicitud s = EmpleadoRolSolicitud.modificar(
            "{\"activo\":false,\"observaciones\":\"    \"}");
        assertFalse(s.getActivo()); assertNull(s.getObservaciones());
        assertNull(s.getIdEmpleado()); assertNull(s.getIdRol());
    }
    @Test void aceptaUnicodeAlrededorDeObservaciones() {
        EmpleadoRolSolicitud s = EmpleadoRolSolicitud.modificar(
            "{\"activo\":true,\"observaciones\":\"\\u200Bnota\\u200B\"}");
        assertEquals("nota", s.getObservaciones());
    }
}
