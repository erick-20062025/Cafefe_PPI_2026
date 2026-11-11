package entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * Asociación entre un producto y un descuento, con porcentaje y vigencia.
 * Las consultas se reutilizan desde la administración de productos.
 */
@Entity
@Table(name = "descuento_producto")
@NamedQueries({
    @NamedQuery(name = "DescuentoProducto.findAll",
            query = "SELECT dp FROM DescuentoProducto dp"),
    @NamedQuery(name = "DescuentoProducto.findByFechaDesde",
            query = "SELECT dp FROM DescuentoProducto dp WHERE dp.fechaDesde = :fechaDesde"),
    @NamedQuery(name = "DescuentoProducto.findByFechaHasta",
            query = "SELECT dp FROM DescuentoProducto dp WHERE dp.fechaHasta = :fechaHasta"),
    @NamedQuery(name = "DescuentoProducto.findByValor",
            query = "SELECT dp FROM DescuentoProducto dp WHERE dp.valor = :valor"),
    @NamedQuery(name = "DescuentoProducto.findByObservaciones",
            query = "SELECT dp FROM DescuentoProducto dp WHERE dp.observaciones = :observaciones"),
    @NamedQuery(name = "DescuentoProducto.findByIdDescuento",
            query = "SELECT dp FROM DescuentoProducto dp "
                  + "WHERE dp.idDescuento.idDescuento = :idDescuento"),
    @NamedQuery(name = "DescuentoProducto.findByIdProducto",
            query = "SELECT dp FROM DescuentoProducto dp "
                  + "JOIN FETCH dp.idDescuento d "
                  + "WHERE dp.idProducto.idProducto = :idProducto "
                  + "ORDER BY d.nombre"),
    /** Descuentos válidos para la fecha comercial y asociados a una orden. */
    @NamedQuery(name = "DescuentoProducto.findAplicablesOrden",
            query = "SELECT DISTINCT dp FROM DescuentoProducto dp, OrdenProducto op, TipoDescuento t "
                  + "WHERE op.idOrden.idOrden = :idOrden "
                  + "AND op.idProducto = dp.idProducto "
                  + "AND dp.idProducto.activo = TRUE "
                  + "AND t.idTipoDescuento = dp.idDescuento.idTipoDescuento "
                  + "AND t.activo = TRUE AND dp.valor > 0 "
                  + "AND dp.valor <= 100 AND dp.valor <= t.descuentoMaximo "
                  + "AND dp.fechaDesde < :finDia "
                  + "AND (dp.fechaHasta IS NULL OR dp.fechaHasta >= :inicioDia) "
                  + "AND dp.idDescuento.fechaDesde < :finDia "
                  + "AND (dp.idDescuento.fechaHasta IS NULL "
                  + "     OR dp.idDescuento.fechaHasta >= :inicioDia)"),
    /** Compatibilidad: promociones válidas para un único producto. */
    @NamedQuery(name = "DescuentoProducto.findAplicables",
            query = "SELECT dp FROM DescuentoProducto dp, TipoDescuento t "
                  + "WHERE dp.idProducto.idProducto = :idProducto "
                  + "AND dp.idProducto.activo = TRUE "
                  + "AND t.idTipoDescuento = dp.idDescuento.idTipoDescuento "
                  + "AND t.activo = TRUE AND dp.valor > 0 "
                  + "AND dp.valor <= 100 AND dp.valor <= t.descuentoMaximo "
                  + "AND dp.fechaDesde < :finDia "
                  + "AND (dp.fechaHasta IS NULL OR dp.fechaHasta >= :inicioDia) "
                  + "AND dp.idDescuento.fechaDesde < :finDia "
                  + "AND (dp.idDescuento.fechaHasta IS NULL "
                  + "     OR dp.idDescuento.fechaHasta >= :inicioDia)"),
    @NamedQuery(name = "DescuentoProducto.countByProductoAndDescuento",
            query = "SELECT COUNT(dp) FROM DescuentoProducto dp "
                  + "WHERE dp.idProducto.idProducto = :idProducto "
                  + "AND dp.idDescuento.idDescuento = :idDescuento")
})
public class DescuentoProducto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @NotNull
    @Column(name = "id_descuento_producto")
    private UUID idDescuentoProducto;

    @Column(name = "fecha_desde")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaDesde;

    @Column(name = "fecha_hasta")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaHasta;

    @Column(name = "valor")
    private Integer valor;

    @Size(max = 2147483647)
    @Column(name = "observaciones")
    private String observaciones;

    @JoinColumn(name = "id_descuento", referencedColumnName = "id_descuento")
    @ManyToOne
    private Descuento idDescuento;

    @JoinColumn(name = "id_producto", referencedColumnName = "id_producto")
    @ManyToOne
    private Producto idProducto;

    public DescuentoProducto() { }

    public DescuentoProducto(UUID idDescuentoProducto) {
        this.idDescuentoProducto = idDescuentoProducto;
    }

    public UUID getIdDescuentoProducto() { return idDescuentoProducto; }
    public void setIdDescuentoProducto(UUID id) { this.idDescuentoProducto = id; }
    public Date getFechaDesde() { return fechaDesde; }
    public void setFechaDesde(Date fechaDesde) { this.fechaDesde = fechaDesde; }
    public Date getFechaHasta() { return fechaHasta; }
    public void setFechaHasta(Date fechaHasta) { this.fechaHasta = fechaHasta; }
    public Integer getValor() { return valor; }
    public void setValor(Integer valor) { this.valor = valor; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Descuento getIdDescuento() { return idDescuento; }
    public void setIdDescuento(Descuento idDescuento) { this.idDescuento = idDescuento; }
    public Producto getIdProducto() { return idProducto; }
    public void setIdProducto(Producto idProducto) { this.idProducto = idProducto; }

    @Override
    public int hashCode() { return idDescuentoProducto != null ? idDescuentoProducto.hashCode() : 0; }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DescuentoProducto)) { return false; }
        DescuentoProducto other = (DescuentoProducto) object;
        return idDescuentoProducto == null
                ? other.idDescuentoProducto == null
                : idDescuentoProducto.equals(other.idDescuentoProducto);
    }

    @Override
    public String toString() {
        return "entity.DescuentoProducto[ idDescuentoProducto=" + idDescuentoProducto + " ]";
    }
}
