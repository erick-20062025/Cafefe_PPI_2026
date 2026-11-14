package boundary.rest.dto;

import entity.ProductoCaracteristica;
import java.util.UUID;

public final class ProductoCaracteristicaDTO {
    private final UUID idProductoCaracteristica;
    private final UUID idProducto;
    private final UUID idCaracteristica;
    private final String valor;

    public ProductoCaracteristicaDTO(ProductoCaracteristica entidad) {
        idProductoCaracteristica = entidad.getIdProductoCaracteristica();
        idProducto = entidad.getIdProducto() == null ? null : entidad.getIdProducto().getIdProducto();
        idCaracteristica = entidad.getIdCaracteristica() == null ? null : entidad.getIdCaracteristica().getIdCaracteristica();
        valor = entidad.getValor();
    }

    public UUID getIdProductoCaracteristica() { return idProductoCaracteristica; }
    public UUID getIdProducto() { return idProducto; }
    public UUID getIdCaracteristica() { return idCaracteristica; }
    public String getValor() { return valor; }
}
