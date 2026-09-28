package control;

import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import entity.FacturaOrdenProducto;

@ Stateless 

    public class FacturaDAO extends DefaultDAO<Factura> {

        @PersistenceContext(unitName = "my_persistence_unit")
        private EntityManager em;

        public FacturaDAO() {
            super(Factura.class);
        }

        @Override
        protected EntityManager getEntityManager() {
            return em;
        }

        public List<Caja> findCajasActivas() {

            return em.createQuery(
                    "SELECT c FROM Caja c "
                    + "WHERE c.activo = true",
                    Caja.class)
                    .getResultList();
        }

        public Caja findCajaById(UUID idCaja) {
            return em.find(Caja.class, idCaja);
        }

        public void crearFacturaCompleta(
                Factura factura,
                List<FacturaOrdenProducto> productos) {

            em.persist(factura);

            for (FacturaOrdenProducto detalle : productos) {

                detalle.setIdFactura(factura);

                em.persist(detalle);
            }
    
        }
        public void eliminarFacturaCompleta(Factura factura) {

            em.createQuery(
                    "DELETE FROM FacturaOrdenProducto fop "
                    + "WHERE fop.idFactura.idFactura = :idFactura"
            )
                    .setParameter(
                            "idFactura",
                            factura.getIdFactura()
                    )
                    .executeUpdate();

            Factura facturaManaged
                    = em.find(
                            Factura.class,
                            factura.getIdFactura()
                    );

            if (facturaManaged != null) {
                em.remove(facturaManaged);
            }
        }
    

    public List<EmpleadoRol> findEmpleadosRolActivos() {

        return em.createQuery(
                "SELECT er FROM EmpleadoRol er "
                + "WHERE er.activo = true",
                EmpleadoRol.class)
                .getResultList();
    }

    public EmpleadoRol findEmpleadoRolById(UUID idEmpleadoRol) {
        return em.find(EmpleadoRol.class, idEmpleadoRol);
    }
}
