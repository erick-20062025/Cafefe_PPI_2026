package boundary.rest;

import boundary.rest.dto.FacturaSolicitud;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FacturaSolicitudTest {
    static final String ID = UUID.randomUUID().toString();
    static final String SOLICITUD = """
            {"idCaja":"%s","idEmpleadoRol":"%s","productos":[{"idOrdenProducto":"%s","precio":1.25}]}
            """.formatted(ID, ID, ID);

    @Test void conservaPrecioYReferencias() {
        var datos = FacturaSolicitud.leer(SOLICITUD, true);
        assertEquals(UUID.fromString(ID), datos.idCaja());
        assertEquals(new BigDecimal("1.25"), datos.productos().getFirst().precio());
    }

    @Test void rechazaUuidYTiposIncorrectos() {
        falla(SOLICITUD.replace(ID, "1-1-1-1-1"), true, 400);
        falla(SOLICITUD.replace("1.25", "\"1.25\""), true, 400);
        falla(SOLICITUD.replace("\"precio\":1.25", "\"precio\":null"), true, 422);
    }

    @Test void limitaTamanoYCamposControladosPorServidor() {
        falla(SOLICITUD + " ".repeat(65536), true, 400);
        falla(SOLICITUD.replaceFirst("\\{", "{\"estado\":\"PAGADA\","), true, 400);
        falla(SOLICITUD.replaceFirst("\\{", "{\"cliente\":\"A\","), false, 400);
        falla(SOLICITUD.replaceFirst("\\{", "{\"observaciones\":\"" + "x".repeat(1001) + "\","), true, 422);
        String detalle = "{\"idOrdenProducto\":\"" + ID + "\",\"precio\":1}";
        falla(SOLICITUD.substring(0, SOLICITUD.indexOf('[')) + "[" + (detalle + ",").repeat(100) + detalle + "]}", true, 422);
    }

    private void falla(String json, boolean creacion, int estado) {
        assertEquals(estado, assertThrows(FacturaSolicitud.DatoInvalido.class,
                () -> FacturaSolicitud.leer(json, creacion)).getEstadoHttp());
    }
}
