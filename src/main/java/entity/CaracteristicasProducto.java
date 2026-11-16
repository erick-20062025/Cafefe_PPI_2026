package entity;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
/** Texto de los valores reales del catálogo, sin tamaños inferidos del nombre. */
public final class CaracteristicasProducto {
    private CaracteristicasProducto() {}
    public static String texto(Producto producto) {
        if (producto == null || producto.getProductoCaracteristicaCollection() == null) return "";
        return producto.getProductoCaracteristicaCollection().stream().filter(Objects::nonNull)
            .filter(pc -> pc.getValor() != null && !pc.getValor().isBlank())
            .map(pc -> (pc.getIdCaracteristica() == null || pc.getIdCaracteristica().getNombre() == null
                ? "" : pc.getIdCaracteristica().getNombre() + ": ") + pc.getValor())
            .distinct().sorted(Comparator.naturalOrder()).collect(Collectors.joining(" · "));
    }
}
