package entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/** Una fila de producto en una orden. El precio es el importe actual. */
@Entity
@Table(name = "orden_producto")
@NamedQueries({
    @NamedQuery(name = "OrdenProducto.findAll", query = "SELECT op FROM OrdenProducto op"),
    @NamedQuery(name = "OrdenProducto.findByPrecio",
            query = "SELECT op FROM OrdenProducto op WHERE op.precio = :precio"),
    @NamedQuery(name = "OrdenProducto.findByObservaciones",
            query = "SELECT op FROM OrdenProducto op WHERE op.observaciones = :observaciones"),
    @NamedQuery(name = "OrdenProducto.findByOrdenConProducto",
            query = "SELECT op FROM OrdenProducto op JOIN FETCH op.idProducto p "
            + "WHERE op.idOrden.idOrden = :idOrden ORDER BY p.nombre"),
    @NamedQuery(name = "OrdenProducto.countByOrden",
            query = "SELECT COUNT(op) FROM OrdenProducto op WHERE op.idOrden.idOrden = :idOrden"),
    @NamedQuery(name = "OrdenProducto.countFacturado",
            query = "SELECT COUNT(f) FROM FacturaOrdenProducto f "
            + "WHERE f.idOrdenProducto.idOrdenProducto = :idOrdenProducto")
})
public class OrdenProducto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @Basic(optional = false) @NotNull
    @Column(name = "id_orden_producto")
    private UUID idOrdenProducto;

    @Column(name = "precio")
    private BigDecimal precio;

    @Size(max = 2147483647)
    @Column(name = "observaciones")
    private String observaciones;

    @OneToMany(mappedBy = "idOrdenProducto")
    private Collection<FacturaOrdenProducto> facturaOrdenProductoCollection;

    @JoinColumn(name = "id_orden", referencedColumnName = "id_orden")
    @ManyToOne
    private Orden idOrden;

    @JoinColumn(name = "id_producto", referencedColumnName = "id_producto")
    @ManyToOne
    private Producto idProducto;

    public OrdenProducto() { }
    public OrdenProducto(UUID id) { this.idOrdenProducto = id; }
    public UUID getIdOrdenProducto() { return idOrdenProducto; }
    public void setIdOrdenProducto(UUID id) { this.idOrdenProducto = id; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String obs) { observaciones = obs; }
    public Collection<FacturaOrdenProducto> getFacturaOrdenProductoCollection() { return facturaOrdenProductoCollection; }
    public void setFacturaOrdenProductoCollection(Collection<FacturaOrdenProducto> v) { facturaOrdenProductoCollection = v; }
    public Orden getIdOrden() { return idOrden; }
    public void setIdOrden(Orden o) { idOrden = o; }
    public Producto getIdProducto() { return idProducto; }
    public void setIdProducto(Producto p) { idProducto = p; }
    @Override public int hashCode() { return Objects.hashCode(idOrdenProducto); }
    @Override public boolean equals(Object obj) {
        return obj instanceof OrdenProducto
                && Objects.equals(idOrdenProducto, ((OrdenProducto)obj).idOrdenProducto);
    }
    @Override public String toString() { return "entity.OrdenProducto[ idOrdenProducto=" + idOrdenProducto + " ]"; }
}
