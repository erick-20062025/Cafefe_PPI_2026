package boundary.rest.dto;

import entity.Producto;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Representacion de Producto para respuestas REST.
 * No expone las colecciones ni las relaciones JPA.
 */
public final class ProductoDTO {

    private final String caracteristicas;
    public String getCaracteristicas() { return caracteristicas; }
    private final BigDecimal precioSugerido;
    private final UUID idProducto;
    private final String nombre;
    private final Boolean activo;
    private final String comentarios;

    public ProductoDTO(Producto producto) {
        this.caracteristicas = entity.CaracteristicasProducto.texto(producto);
        this.precioSugerido = producto.getPrecioSugerido();
        this.idProducto = producto.getIdProducto();
        this.nombre = producto.getNombre();
        this.activo = producto.getActivo();
        this.comentarios = producto.getComentarios();
    }

    public BigDecimal getPrecioSugerido() { return precioSugerido; }
    public UUID getIdProducto() { return idProducto; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getComentarios() { return comentarios; }
}
