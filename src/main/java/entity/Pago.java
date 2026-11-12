package entity;

import jakarta.json.bind.annotation.JsonbTransient;
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
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Cabecera del cobro de una factura. Puede contener múltiples PagoDetalle.
 * Las consultas usadas exclusivamente por el módulo de cobros se declaran
 * aquí, incluso cuando su resultado es Factura o FacturaOrdenProducto, para no
 * modificar las entidades que está desarrollando otro integrante.
 */
@Entity
@Table(name = "pago")
@NamedQueries({
    @NamedQuery(name = "Pago.findAll", query = "SELECT p FROM Pago p"),
    @NamedQuery(name = "Pago.findByEstado", query = "SELECT p FROM Pago p WHERE p.estado = :estado"),
    @NamedQuery(name = "Pago.findByObservaciones", query = "SELECT p FROM Pago p WHERE p.observaciones = :observaciones"),
    @NamedQuery(name = "Pago.findByFactura", query = "SELECT p FROM Pago p WHERE p.idFactura.idFactura = :idFactura"),
    @NamedQuery(name = "Pago.facturasPendientes", query = "SELECT f FROM Factura f WHERE UPPER(TRIM(f.estado)) = 'PENDIENTE' ORDER BY f.fechaFacturacion DESC"),
    @NamedQuery(name = "Pago.detallesFactura", query = "SELECT fop FROM FacturaOrdenProducto fop JOIN FETCH fop.idOrdenProducto op JOIN FETCH op.idProducto WHERE fop.idFactura.idFactura = :idFactura"),
    @NamedQuery(name = "Pago.totalFactura", query = "SELECT SUM(fop.precio) FROM FacturaOrdenProducto fop WHERE fop.idFactura.idFactura = :idFactura"),
    @NamedQuery(name = "Pago.detallesInvalidosFactura", query = "SELECT COUNT(fop) FROM FacturaOrdenProducto fop WHERE fop.idFactura.idFactura = :idFactura AND (fop.precio IS NULL OR fop.precio < 0)")
})
public class Pago implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @NotNull
    @Column(name = "id_pago")
    private UUID idPago;

    @Size(max = 20)
    @Column(name = "estado")
    private String estado;

    @Size(max = 2147483647)
    @Column(name = "observaciones")
    private String observaciones;

    @JoinColumn(name = "id_factura", referencedColumnName = "id_factura")
    @ManyToOne
    private Factura idFactura;

    @OneToMany(mappedBy = "idPago")
    private Collection<PagoDetalle> pagoDetalleCollection;

    public Pago() { }
    public Pago(UUID idPago) { this.idPago = idPago; }

    public UUID getIdPago() { return idPago; }
    public void setIdPago(UUID idPago) { this.idPago = idPago; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Factura getIdFactura() { return idFactura; }
    public void setIdFactura(Factura idFactura) { this.idFactura = idFactura; }

    @JsonbTransient
    public Collection<PagoDetalle> getPagoDetalleCollection() { return pagoDetalleCollection; }
    public void setPagoDetalleCollection(Collection<PagoDetalle> pagoDetalleCollection) {
        this.pagoDetalleCollection = pagoDetalleCollection;
    }

    @Override
    public int hashCode() { return Objects.hashCode(idPago); }
    @Override
    public boolean equals(Object objeto) {
        if (!(objeto instanceof Pago otro)) return false;
        return Objects.equals(idPago, otro.idPago);
    }
    @Override
    public String toString() { return "entity.Pago[ idPago=" + idPago + " ]"; }
}
