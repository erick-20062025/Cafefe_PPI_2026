package control;

import jakarta.persistence.EntityManager;
import java.util.List;

public abstract class DefaultDAO<T> implements DAOInterface<T> {

    private final Class<T> entityClass;

    public DefaultDAO(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    protected abstract EntityManager getEntityManager();

    @Override
    public void crear(T reg) {
        getEntityManager().persist(reg);
    }

    @Override
    public void modificar(T reg) {
        getEntityManager().merge(reg);
    }

    @Override
    public void eliminar(T reg) {
        getEntityManager().remove(getEntityManager().merge(reg));
    }

    @Override
    public List<T> findAll() {
        jakarta.persistence.criteria.CriteriaQuery cq = getEntityManager().getCriteriaBuilder().createQuery();
        cq.select(cq.from(entityClass));
        return getEntityManager().createQuery(cq).getResultList();
    }

    @Override
    public T findById(Object id) {
        return getEntityManager().find(entityClass, id);
    }
}