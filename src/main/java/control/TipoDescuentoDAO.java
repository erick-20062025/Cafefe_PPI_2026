package control;

import entity.TipoDescuento;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import validation.TipoDescuentoValidator;

@Stateless
public class TipoDescuentoDAO extends DefaultDAO<TipoDescuento> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public TipoDescuentoDAO() {
        super(TipoDescuento.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    public void crear(TipoDescuento reg) {
        TipoDescuentoValidator.validar(reg);
        super.crear(reg);
    }

    @Override
    public void modificar(TipoDescuento reg) {
        TipoDescuentoValidator.validar(reg);
        super.modificar(reg);
    }

}
