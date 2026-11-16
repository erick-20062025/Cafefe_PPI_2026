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
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** Cabecera de una orden, asociada al empleado que la tomó. */
@Entity
@Table(name = "orden")
@NamedQueries({
    @NamedQuery(name = "Orden.findAll", query = "SELECT o FROM Orden o"),
    @NamedQuery(name = "Orden.findByFechaCreacion",
            query = "SELECT o FROM Orden o WHERE o.fechaCreacion = :fechaCreacion"),
    // Consultas auxiliares de la pantalla de órdenes. Toda la JPQL se declara
    // en las entidades, no se construye mediante cadenas en el DAO.
    @NamedQuery(name = "Orden.findPendientesPago",
            query = "SELECT DISTINCT o FROM Orden o JOIN o.ordenProductoCollection op "
            + "WHERE (o.estadoPreparacion IS NULL OR o.estadoPreparacion <> 'CANCELADA') AND NOT EXISTS (SELECT fp FROM FacturaOrdenProducto fp "
            + "WHERE fp.idOrdenProducto = op "
            + "AND UPPER(fp.idFactura.estado) = 'PAGADA') "
            + "ORDER BY o.fechaCreacion DESC"),
    @NamedQuery(name = "Orden.findCamarerosActivos",
            query = "SELECT er FROM EmpleadoRol er "
            + "JOIN FETCH er.idEmpleado e JOIN FETCH er.idRol r "
            + "WHERE er.activo = TRUE AND e.activo = TRUE AND r.activo = TRUE "
            + "AND LOWER(TRIM(r.nombre)) = :nombreRol "
            + "ORDER BY e.nombre, e.apellido"),
    @NamedQuery(name = "Orden.findCamareroActivoById",
            query = "SELECT er FROM EmpleadoRol er "
            + "JOIN FETCH er.idEmpleado e JOIN FETCH er.idRol r "
            + "WHERE er.idEmpleadoRol = :idEmpleadoRol "
            + "AND er.activo = TRUE AND e.activo = TRUE AND r.activo = TRUE "
            + "AND LOWER(TRIM(r.nombre)) = :nombreRol")
})
public class Orden implements Serializable {
    @Column(name="cancelada_por") private String canceladaPor;
    @Column(name="cancelada_en") @Temporal(TemporalType.TIMESTAMP) private Date canceladaEn;
    public String getCanceladaPor() { return canceladaPor; }
    public void setCanceladaPor(String valor) { canceladaPor=valor; }
    public Date getCanceladaEn() { return canceladaEn; }
    public void setCanceladaEn(Date valor) { canceladaEn=valor; }
    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @NotNull
    @Column(name = "id_orden")
    private UUID idOrden;

    @Column(name = "fecha_creacion")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaCreacion;

    @OneToMany(mappedBy = "idOrden")
    private Collection<OrdenProducto> ordenProductoCollection;

    @JoinColumn(name = "id_empleado_rol", referencedColumnName = "id_empleado_rol")
    @ManyToOne
    private EmpleadoRol idEmpleadoRol;

    @Column(name="estado_preparacion") private String estadoPreparacion;
    public String getEstadoPreparacion() { return estadoPreparacion; }
    public void setEstadoPreparacion(String valor) { estadoPreparacion = valor; }
    @Column(name="cliente_web") private String clienteWeb;
    public String getClienteWeb() { return clienteWeb; }
    public void setClienteWeb(String valor) { clienteWeb = valor; }
    @Column(name="atendido_por") private String atendidoPor;
    public String getAtendidoPor() { return atendidoPor; }
    public void setAtendidoPor(String valor) { atendidoPor = valor; }
    public Orden() { }
    public Orden(UUID idOrden) { this.idOrden = idOrden; }
    public UUID getIdOrden() { return idOrden; }
    public void setIdOrden(UUID idOrden) { this.idOrden = idOrden; }
    public Date getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Date fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Collection<OrdenProducto> getOrdenProductoCollection() { return ordenProductoCollection; }
    public void setOrdenProductoCollection(Collection<OrdenProducto> value) { ordenProductoCollection = value; }
    public EmpleadoRol getIdEmpleadoRol() { return idEmpleadoRol; }
    public void setIdEmpleadoRol(EmpleadoRol idEmpleadoRol) { this.idEmpleadoRol = idEmpleadoRol; }

    @Override public int hashCode() { return Objects.hashCode(idOrden); }
    @Override public boolean equals(Object object) {
        if (!(object instanceof Orden)) return false;
        return Objects.equals(idOrden, ((Orden) object).idOrden);
    }
    @Override public String toString() { return "entity.Orden[ idOrden=" + idOrden + " ]"; }
}
