package control;

import java.util.List;

public interface DAOInterface<T> {
    public void crear(T reg);
    public void modificar(T reg);
    public void eliminar(T reg);
    public List<T> findAll();
    public T findById(Object id);
}       