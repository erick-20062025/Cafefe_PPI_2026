
package control;

import entity.Rol;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a datos y validaciones de persistencia de roles.
 *
 * Las consultas JPQL se declaran en la entidad Rol.
 * Las operaciones de escritura utilizan las transacciones
 * administradas por el contenedor EJB.
 */
@Stateless
public class RolDAO extends DefaultDAO<Rol> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public RolDAO() {
        super(Rol.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Obtiene todos los roles mediante una consulta nombrada.
     *
     * @return lista de roles, posiblemente vacía
     */
    @Override
    public List<Rol> findAll() {
        return em.createNamedQuery(
                "Rol.findAll", Rol.class
        ).getResultList();
    }

    /**
     * Busca un rol por su identificador.
     *
     * @param id identificador UUID del rol
     * @return rol encontrado o null si no existe
     * @throws IllegalArgumentException si el ID es inválido
     */
    @Override
    public Rol findById(Object id) {

        if (!(id instanceof UUID)) {
            throw new IllegalArgumentException(
                    "El identificador del rol no es válido");
        }

        return em.find(Rol.class, id);
    }

    /**
     * Crea un rol después de validar sus datos.
     *
     * @param reg rol que se desea registrar
     * @throws IllegalArgumentException si los datos son
     * inválidos o el identificador ya existe
     */
    @Override
    public void crear(Rol reg) {

        validarRol(reg);

        if (em.find(Rol.class, reg.getIdRol()) != null) {
            throw new IllegalArgumentException(
                    "Ya existe un rol con ese identificador");
        }

        em.persist(reg);
    }

    /**
     * Modifica únicamente roles existentes.
     *
     * @param reg rol con los nuevos datos
     * @throws IllegalArgumentException si los datos son
     * inválidos o el rol no existe
     */
    @Override
    public void modificar(Rol reg) {

        validarRol(reg);

        if (em.find(Rol.class, reg.getIdRol()) == null) {
            throw new IllegalArgumentException(
                    "El rol que intenta modificar no existe");
        }

        em.merge(reg);
    }

    /**
     * Elimina un rol existente.
     *
     * Las restricciones de claves foráneas de PostgreSQL
     * deben proteger los roles asociados a empleados.
     *
     * @param reg rol que se desea eliminar
     * @throws IllegalArgumentException si no existe
     * o su identificador es inválido
     */
    @Override
    public void eliminar(Rol reg) {

        if (reg == null || reg.getIdRol() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un rol válido");
        }

        Rol administrado = em.find(
                Rol.class, reg.getIdRol());

        if (administrado == null) {
            throw new IllegalArgumentException(
                    "El rol ya no existe");
        }

        em.remove(administrado);
    }

    /**
     * Valida los datos obligatorios del rol.
     *
     * @param rol entidad a validar
     * @throws IllegalArgumentException cuando algún
     * dato obligatorio no cumple las reglas
     */
    private void validarRol(Rol rol) {

        if (rol == null) {
            throw new IllegalArgumentException(
                    "El rol no puede ser nulo");
        }

        if (rol.getIdRol() == null) {
            throw new IllegalArgumentException(
                    "El identificador del rol es obligatorio");
        }

        String nombre = rol.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del rol es obligatorio");
        }

        nombre = nombre.trim();

        if (nombre.length() < 2 || nombre.length() > 155) {
            throw new IllegalArgumentException(
                    "El nombre debe tener entre 2 y 155 caracteres");
        }

        if (rol.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado del rol");
        }

        rol.setNombre(nombre);
    }
}
