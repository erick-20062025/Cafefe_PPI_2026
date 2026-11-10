
package control;

import entity.Empleado;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a datos del módulo Empleado.
 *
 * Valida los campos obligatorios y protege empleados
 * que tengan asignaciones de roles existentes.
 *
 * Las transacciones son administradas por EJB/JTA.
 */
@Stateless
public class EmpleadoDAO extends DefaultDAO<Empleado> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public EmpleadoDAO() {
        super(Empleado.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Obtiene todos los empleados.
     *
     * @return lista de empleados, posiblemente vacía
     */
    @Override
    public List<Empleado> findAll() {
        return em.createNamedQuery(
                "Empleado.findAll", Empleado.class
        ).getResultList();
    }

    /**
     * Busca un empleado por su identificador.
     *
     * @param id UUID del empleado
     * @return empleado o null si no existe
     */
    @Override
    public Empleado findById(Object id) {

        if (!(id instanceof UUID)) {
            throw new IllegalArgumentException(
                    "Identificador de empleado inválido");
        }

        return em.find(Empleado.class, id);
    }

    /**
     * Registra un empleado después de validar sus datos.
     *
     * @param empleado empleado que será creado
     * @throws IllegalArgumentException si los datos son inválidos
     */
    @Override
    public void crear(Empleado empleado) {

        validarEmpleado(empleado);

        if (em.find(Empleado.class,
                empleado.getIdEmpleado()) != null) {
            throw new IllegalArgumentException(
                    "Ya existe un empleado con ese identificador");
        }

        em.persist(empleado);
    }

    /**
     * Actualiza únicamente un empleado existente.
     *
     * @param empleado empleado con los nuevos valores
     */
    @Override
    public void modificar(Empleado empleado) {

        validarEmpleado(empleado);

        if (em.find(Empleado.class,
                empleado.getIdEmpleado()) == null) {
            throw new IllegalArgumentException(
                    "El empleado que intenta modificar no existe");
        }

        em.merge(empleado);
    }

    /**
     * Elimina un empleado solo cuando no tiene
     * relaciones de roles asociadas.
     *
     * No elimina automáticamente relaciones históricas.
     *
     * @param empleado empleado seleccionado
     */
    @Override
    public void eliminar(Empleado empleado) {

        if (empleado == null
                || empleado.getIdEmpleado() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un empleado válido");
        }

        UUID id = empleado.getIdEmpleado();

        Empleado administrado = em.find(Empleado.class, id);

        if (administrado == null) {
            throw new IllegalArgumentException(
                    "El empleado ya no existe");
        }

        Long relaciones = em.createNamedQuery(
                "EmpleadoRol.countByIdEmpleado", Long.class
        )
        .setParameter("idEmpleado", id)
        .getSingleResult();

        if (relaciones > 0) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el empleado "
                    + "porque tiene roles asociados");
        }

        em.remove(administrado);
        em.flush();
    }

    /**
     * Valida el contenido de un empleado.
     *
     * @param empleado entidad que será validada
     * @throws IllegalArgumentException si falta
     * información o se superan los límites permitidos
     */
    private void validarEmpleado(Empleado empleado) {

        if (empleado == null) {
            throw new IllegalArgumentException(
                    "El empleado no puede ser nulo");
        }

        if (empleado.getIdEmpleado() == null) {
            throw new IllegalArgumentException(
                    "El identificador es obligatorio");
        }

        String nombre = empleado.getNombre();
        String apellido = empleado.getApellido();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre es obligatorio");
        }

        if (apellido == null || apellido.isBlank()) {
            throw new IllegalArgumentException(
                    "El apellido es obligatorio");
        }

        nombre = nombre.trim();
        apellido = apellido.trim();

        if (nombre.length() < 2 || nombre.length() > 255) {
            throw new IllegalArgumentException(
                    "El nombre debe tener entre 2 y 255 caracteres");
        }

        if (apellido.length() < 2 || apellido.length() > 255) {
            throw new IllegalArgumentException(
                    "El apellido debe tener entre 2 y 255 caracteres");
        }

        if (empleado.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado del empleado");
        }

        empleado.setNombre(nombre);
        empleado.setApellido(apellido);
    }
}
