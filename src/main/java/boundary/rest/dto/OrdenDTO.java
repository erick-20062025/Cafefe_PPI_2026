package boundary.rest.dto;

import entity.Orden;
import entity.OrdenProducto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta explícita para evitar serializar los ciclos de entidades JPA.
 * Los datos de empleados, facturas y relaciones internas no se devuelven.
 */
public class OrdenDTO {
    private UUID idOrden;
    private String fechaCreacion;
    private UUID idEmpleadoRol;
    private BigDecimal total;
    private List<ProductoDTO> productos;

    public OrdenDTO() { }

    public static OrdenDTO resumen(Orden orden) {
        OrdenDTO salida = new OrdenDTO();
        salida.idOrden = orden.getIdOrden();
        salida.fechaCreacion = orden.getFechaCreacion() == null ? null
                : Instant.ofEpochMilli(orden.getFechaCreacion().getTime()).toString();
        salida.idEmpleadoRol = orden.getIdEmpleadoRol() == null ? null
                : orden.getIdEmpleadoRol().getIdEmpleadoRol();
        return salida;
    }

    public static OrdenDTO detalle(Orden orden, List<OrdenProducto> filas) {
        OrdenDTO salida = resumen(orden);
        salida.productos = new ArrayList<>();
        salida.total = BigDecimal.ZERO.setScale(2);
        if (filas != null) {
            for (OrdenProducto fila : filas) {
                ProductoDTO item = ProductoDTO.desde(fila);
                salida.productos.add(item);
                if (item.getPrecio() != null) salida.total = salida.total.add(item.getPrecio());
            }
        }
        return salida;
    }

    public UUID getIdOrden() { return idOrden; }
    public void setIdOrden(UUID idOrden) { this.idOrden = idOrden; }
    public String getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(String fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public UUID getIdEmpleadoRol() { return idEmpleadoRol; }
    public void setIdEmpleadoRol(UUID idEmpleadoRol) { this.idEmpleadoRol = idEmpleadoRol; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public List<ProductoDTO> getProductos() { return productos; }
    public void setProductos(List<ProductoDTO> productos) { this.productos = productos; }

    public static class ProductoDTO {
        private UUID idOrdenProducto;
        private UUID idProducto;
        private String producto;
        private BigDecimal precio;
        private String observaciones;
        public ProductoDTO() { }
        public static ProductoDTO desde(OrdenProducto fila) {
            ProductoDTO dto = new ProductoDTO();
            dto.idOrdenProducto = fila.getIdOrdenProducto();
            dto.idProducto = fila.getIdProducto() == null ? null
                    : fila.getIdProducto().getIdProducto();
            dto.producto = fila.getIdProducto() == null ? null
                    : fila.getIdProducto().getNombre();
            dto.precio = fila.getPrecio();
            dto.observaciones = fila.getObservaciones();
            return dto;
        }
        public UUID getIdOrdenProducto() { return idOrdenProducto; }
        public void setIdOrdenProducto(UUID id) { this.idOrdenProducto = id; }
        public UUID getIdProducto() { return idProducto; }
        public void setIdProducto(UUID id) { this.idProducto = id; }
        public String getProducto() { return producto; }
        public void setProducto(String producto) { this.producto = producto; }
        public BigDecimal getPrecio() { return precio; }
        public void setPrecio(BigDecimal precio) { this.precio = precio; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }

    public static class PromocionDTO {
        private UUID idDescuento;
        private String nombre;
        public PromocionDTO() { }
        public PromocionDTO(UUID idDescuento, String nombre) {
            this.idDescuento = idDescuento;
            this.nombre = nombre;
        }
        public UUID getIdDescuento() { return idDescuento; }
        public void setIdDescuento(UUID idDescuento) { this.idDescuento = idDescuento; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
    }
}
