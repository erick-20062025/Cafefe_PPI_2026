package control;

import entity.PagoDetalle;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/** Consultas de los abonos. Los registros nuevos se crean solo por PagoDAO. */
@Stateless
public class PagoDetalleDAO {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public List<PagoDetalle> findByFactura(UUID idFactura) {
        if (idFactura == null) {
            throw new IllegalArgumentException("Debe seleccionar una factura");
        }
        return em.createNamedQuery("PagoDetalle.findByFactura", PagoDetalle.class)
                .setParameter("idFactura", idFactura)
                .getResultList();
    }
}
