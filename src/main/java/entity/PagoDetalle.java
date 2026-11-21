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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Cada abono y su método de pago dentro de un Pago. */
@Entity
@Table(name = "pago_detalle")
@NamedQueries({
    @NamedQuery(name = "PagoDetalle.findAll", query = "SELECT pd FROM PagoDetalle pd"),
    @NamedQuery(name = "PagoDetalle.findByMonto", query = "SELECT pd FROM PagoDetalle pd WHERE pd.monto = :monto"),
    @NamedQuery(name = "PagoDetalle.findByTipoPago", query = "SELECT pd FROM PagoDetalle pd WHERE pd.tipoPago = :tipoPago"),
    @NamedQuery(name = "PagoDetalle.findByObservaciones", query = "SELECT pd FROM PagoDetalle pd WHERE pd.observaciones = :observaciones"),
    @NamedQuery(name = "PagoDetalle.findByReferenciaExterna", query = "SELECT pd FROM PagoDetalle pd WHERE pd.referenciaExterna = :referenciaExterna"),
    @NamedQuery(name = "PagoDetalle.findByFactura", query = "SELECT pd FROM PagoDetalle pd JOIN FETCH pd.idPago p WHERE p.idFactura.idFactura = :idFactura ORDER BY pd.tipoPago, pd.idPagoDetalle"),
    @NamedQuery(name = "PagoDetalle.totalAbonadoFactura", query = "SELECT SUM(pd.monto) FROM PagoDetalle pd WHERE pd.idPago.idFactura.idFactura = :idFactura AND pd.idPago.estado <> 'ANULADO'"),
    @NamedQuery(name = "PagoDetalle.countReferenciaFactura", query = "SELECT COUNT(pd) FROM PagoDetalle pd WHERE pd.idPago.idFactura.idFactura = :idFactura AND pd.tipoPago = :tipoPago AND pd.referenciaExterna = :referenciaExterna AND pd.idPago.estado <> 'ANULADO'")
})
public class PagoDetalle implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @NotNull
    @Column(name = "id_pago_detalle")
    private UUID idPagoDetalle;

    @Column(name = "monto")
    private BigDecimal monto;

    @Size(max = 20)
    @Column(name = "tipo_pago")
    private String tipoPago;

    @Size(max = 2147483647)
    @Column(name = "observaciones")
    private String observaciones;

    @Size(max = 2147483647)
    @Column(name = "referencia_externa")
    private String referenciaExterna;

    @JoinColumn(name = "id_pago", referencedColumnName = "id_pago")
    @ManyToOne
    private Pago idPago;

    public PagoDetalle() { }
    public PagoDetalle(UUID idPagoDetalle) { this.idPagoDetalle = idPagoDetalle; }

    public UUID getIdPagoDetalle() { return idPagoDetalle; }
    public void setIdPagoDetalle(UUID idPagoDetalle) { this.idPagoDetalle = idPagoDetalle; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getTipoPago() { return tipoPago; }
    public void setTipoPago(String tipoPago) { this.tipoPago = tipoPago; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) { this.referenciaExterna = referenciaExterna; }
    public Pago getIdPago() { return idPago; }
    public void setIdPago(Pago idPago) { this.idPago = idPago; }

    @Override
    public int hashCode() { return Objects.hashCode(idPagoDetalle); }
    @Override
    public boolean equals(Object objeto) {
        if (!(objeto instanceof PagoDetalle otro)) return false;
        return Objects.equals(idPagoDetalle, otro.idPagoDetalle);
    }
    @Override
    public String toString() { return "entity.PagoDetalle[ idPagoDetalle=" + idPagoDetalle + " ]"; }
}
