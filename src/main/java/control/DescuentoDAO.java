package control;

import entity.Descuento;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import validation.DescuentoValidator;
import entity.TipoDescuento;
import java.time.LocalDate;
import java.time.ZoneId;

@Stateless
public class DescuentoDAO extends DefaultDAO<Descuento> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public DescuentoDAO() {
        super(Descuento.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    public void crear(Descuento descuento) {
        DescuentoValidator.validar(descuento);

        LocalDate fechaDesde = descuento.getFechaDesde()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        if (fechaDesde.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser anterior a hoy");
        }

        TipoDescuento tipo = em.find(
                TipoDescuento.class,
                descuento.getIdTipoDescuento()
        );

        if (tipo == null) {
            throw new IllegalArgumentException(
                    "El tipo de descuento seleccionado no existe");
        }

        if (!Boolean.TRUE.equals(tipo.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede asignar un tipo de descuento inactivo");
        }

        super.crear(descuento);
    }

    @Override
    public void modificar(Descuento descuento) {
        DescuentoValidator.validar(descuento);

        Descuento existente = em.find(
                Descuento.class,
                descuento.getIdDescuento()
        );

        if (existente == null) {
            throw new IllegalArgumentException(
                    "El descuento que intenta modificar no existe");
        }

        if (!existente.getIdTipoDescuento().equals(
                descuento.getIdTipoDescuento())) {

            throw new IllegalArgumentException(
                    "No se puede cambiar el tipo de un descuento existente");
        }

        TipoDescuento tipo = em.find(
                TipoDescuento.class,
                descuento.getIdTipoDescuento()
        );

        if (tipo == null) {
            throw new IllegalArgumentException(
                    "El tipo de descuento seleccionado no existe");
        }

        // Se permite conservar un tipo que fue desactivado.
        super.modificar(descuento);
    }

    /**
     * Busca los descuentos asociados a un tipo de descuento.
     *
     * @param idTipoDescuento identificador del tipo de descuento
     * @return lista de descuentos asociados
     */
    public List<Descuento> findByTipoDescuento(UUID idTipoDescuento) {

        return em.createQuery(
                "SELECT d FROM Descuento d "
                + "WHERE d.idTipoDescuento = :idTipoDescuento",
                Descuento.class
        )
                .setParameter("idTipoDescuento", idTipoDescuento)
                .getResultList();
    }
}
