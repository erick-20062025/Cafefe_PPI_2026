package control;
import entity.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ImportesOrdenTest {
    final Date fecha = new Date(5000);
    DescuentoProducto promo(int porcentaje, long inicio, long fin) {
        Descuento d = new Descuento(UUID.randomUUID()); d.setFechaDesde(new Date(inicio)); d.setFechaHasta(new Date(fin));
        DescuentoProducto dp = new DescuentoProducto(UUID.randomUUID()); dp.setIdDescuento(d); dp.setValor(porcentaje);
        dp.setFechaDesde(new Date(inicio)); dp.setFechaHasta(new Date(fin)); return dp;
    }
    @Test void sinPromocionConservaPrecio() { assertEquals(new BigDecimal("5.00"), ImportesOrden.calcular(new BigDecimal("5"),fecha,List.of()).total()); }
    @Test void redondeaPorUnidadHalfUp() { var r=ImportesOrden.calcular(new BigDecimal("0.05"),fecha,List.of(promo(10,0,10000))); assertEquals(new BigDecimal("0.01"),r.descuento()); assertEquals(new BigDecimal("0.04"),r.total()); }
    @Test void noAcumulaYSeleccionaMayor() { var r=ImportesOrden.calcular(new BigDecimal("10"),fecha,List.of(promo(10,0,10000),promo(20,0,10000))); assertEquals(new BigDecimal("8.00"),r.total()); }
    @Test void rechazaVencidasFuturasYPorcentajesInvalidos() { assertEquals(new BigDecimal("10.00"),ImportesOrden.calcular(new BigDecimal("10"),fecha,List.of(promo(50,0,4999),promo(50,5001,10000),promo(101,0,10000),promo(-5,0,10000))).total()); }
    @Test void admiteLimitesInclusiveYDescuentoTotal() { assertEquals(new BigDecimal("0.00"),ImportesOrden.calcular(new BigDecimal("10"),fecha,List.of(promo(100,5000,5000))).total()); }
    @Test void asociacionVigenteNoResucitaPromocionVencida() { var p=promo(50,0,10000);p.getIdDescuento().setFechaHasta(new Date(4999)); assertEquals(new BigDecimal("10.00"),ImportesOrden.calcular(new BigDecimal("10"),fecha,List.of(p)).total()); }
    @Test void resultadoNoCambiaAlEditarPromocion() { var p=promo(20,0,10000);var r=ImportesOrden.calcular(new BigDecimal("10"),fecha,List.of(p));p.setValor(80);assertEquals(new BigDecimal("8.00"),r.total()); }
    @Test void rechazaPreciosNegativos() { assertThrows(IllegalArgumentException.class,()->ImportesOrden.calcular(new BigDecimal("-1"),fecha,List.of())); }
}
