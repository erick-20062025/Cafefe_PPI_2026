package control;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class RecepcionDAOTest {
    final UUID orden=UUID.randomUUID(), factura=UUID.randomUUID();
    Object[] fila(UUID f, String total, String abono, String nota) {
        return new Object[]{orden,new Date(),nota,f,total==null?null:new BigDecimal(total),new BigDecimal(abono),total!=null};
    }
    @Test void sinFacturaEsPendienteYRecuperaNombre() {
        var r=RecepcionDAO.resumir(Collections.singletonList(fila(null,null,"0","Cliente: peña | Sin azúcar"))).get(0);
        assertEquals("peña",r.getCliente()); assertEquals("PENDIENTE_DE_PAGO",r.getEstadoPago());
    }
    @Test void duplicarFilasNoMultiplicaAbonos() {
        var f=fila(factura,"20","10",null);
        assertEquals("ABONADA_PARCIALMENTE",RecepcionDAO.resumir(List.of(f,f)).get(0).getEstadoPago());
    }
    @Test void facturaCompartidaDebeEstarTotalmenteCubierta() {
        assertEquals("ABONADA_PARCIALMENTE",RecepcionDAO.resumir(Collections.singletonList(fila(factura,"100","90",null))).get(0).getEstadoPago());
    }
    @Test void todasLasFilasDebenTenerFacturaCubierta() {
        assertEquals("ABONADA_PARCIALMENTE",RecepcionDAO.resumir(List.of(fila(factura,"20","20",null),fila(null,null,"0",null))).get(0).getEstadoPago());
        assertEquals("PAGADA",RecepcionDAO.resumir(Collections.singletonList(fila(factura,"20","20",null))).get(0).getEstadoPago());
    }
    @Test void antiguaSinNombreNoInventaCliente() {assertNull(RecepcionDAO.resumir(Collections.singletonList(fila(null,null,"0","Sin azúcar"))).get(0).getCliente());}
}
