package control;

import entity.DescuentoProducto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Una promoción por unidad: la de mayor porcentaje vigente; empates por UUID.
 * No se acumulan promociones, ni se recalculan las filas guardadas.
 */
public final class ImportesOrden {
    private ImportesOrden() {}
    public record Resultado(BigDecimal base, BigDecimal descuento, BigDecimal total, UUID promocion) {}
    public static Resultado calcular(BigDecimal precio, Date fecha, List<DescuentoProducto> candidatas) {
        if (precio == null || precio.signum() < 0 || fecha == null) throw new IllegalArgumentException("Precio o fecha inválidos");
        BigDecimal base = precio.setScale(2, RoundingMode.UNNECESSARY);
        DescuentoProducto mejor = (candidatas == null ? List.<DescuentoProducto>of() : candidatas).stream()
            .filter(dp -> dp != null && dp.getValor() != null && dp.getValor() > 0 && dp.getValor() <= 100
                && dp.getIdDescuento() != null && dp.getIdDescuento().getIdDescuento() != null
                && vigente(fecha, dp.getFechaDesde(), dp.getFechaHasta())
                && vigente(fecha, dp.getIdDescuento().getFechaDesde(), dp.getIdDescuento().getFechaHasta()))
            .sorted(Comparator.comparing(DescuentoProducto::getValor).reversed()
                .thenComparing(dp -> dp.getIdDescuento().getIdDescuento()))
            .findFirst().orElse(null);
        BigDecimal descuento = mejor == null ? BigDecimal.ZERO.setScale(2)
            : base.multiply(BigDecimal.valueOf(mejor.getValor())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Resultado(base, descuento, base.subtract(descuento).max(BigDecimal.ZERO).setScale(2),
            mejor == null ? null : mejor.getIdDescuento().getIdDescuento());
    }
    private static boolean vigente(Date fecha, Date desde, Date hasta) {
        return desde != null && !fecha.before(desde) && (hasta == null || !fecha.after(hasta));
    }
}
