package control;

import entity.Empleado;
import entity.EmpleadoRol;
import entity.Rol;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/**
 * Gestiona las relaciones entre empleados y roles.
 *
 * Evita asociaciones duplicadas y protege asignaciones utilizadas por órdenes o
 * facturas.
 */
@Stateless
public class EmpleadoRolDAO
        extends DefaultDAO<EmpleadoRol> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public EmpleadoRolDAO() {
        super(EmpleadoRol.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Obtiene todas las asignaciones de roles.
     */
    @Override
    public List<EmpleadoRol> findAll() {
        return em.createNamedQuery(
                "EmpleadoRol.findAll", EmpleadoRol.class
        ).getResultList();
    }

    /**
     * Consulta una asignación mediante su UUID.
     */
    @Override
    public EmpleadoRol findById(Object id) {

        if (!(id instanceof UUID)) {
            throw new IllegalArgumentException(
                    "Identificador de relación inválido");
        }

        return em.find(EmpleadoRol.class, id);
    }

    /**
     * Busca las asignaciones de un empleado.
     *
     * @param idEmpleado identificador del empleado
     * @return relaciones encontradas
     */
    public List<EmpleadoRol> findByIdEmpleado(UUID idEmpleado) {

        validarId(idEmpleado, "empleado");

        return em.createNamedQuery(
                "EmpleadoRol.findByIdEmpleado",
                EmpleadoRol.class
        )
                .setParameter("idEmpleado", idEmpleado)
                .getResultList();
    }

    /** Proyección escalar: estados activos evaluados por PostgreSQL en cada acceso. */
    public List<String> findRolesActivosParaSesion(UUID idEmpleado) {
        validarId(idEmpleado, "empleado");
        return em.createQuery("SELECT r.nombre FROM EmpleadoRol er JOIN er.idRol r JOIN er.idEmpleado e "
                + "WHERE e.idEmpleado = :id AND e.activo = TRUE AND er.activo = TRUE AND r.activo = TRUE", String.class)
                .setParameter("id", idEmpleado).getResultList();
    }

    /**
     * Cuenta todas las relaciones del empleado.
     */
    public long countByIdEmpleado(UUID idEmpleado) {

        validarId(idEmpleado, "empleado");

        return em.createNamedQuery(
                "EmpleadoRol.countByIdEmpleado",
                Long.class
        )
                .setParameter("idEmpleado", idEmpleado)
                .getSingleResult();
    }

    /**
     * Comprueba si una combinación empleado-rol ya está registrada, incluso si
     * está inactiva.
     */
    public boolean existeRelacion(
            UUID idEmpleado, UUID idRol) {

        validarId(idEmpleado, "empleado");
        validarId(idRol, "rol");

        Long cantidad = em.createNamedQuery(
                "EmpleadoRol.countByEmpleadoAndRol",
                Long.class
        )
                .setParameter("idEmpleado", idEmpleado)
                .setParameter("idRol", idRol)
                .getSingleResult();

        return cantidad > 0;
    }

    /**
     * Registra una relación válida entre empleado y rol.
     *
     * Verifica que ambos existan y estén activos, además de impedir
     * asignaciones duplicadas.
     *
     * @param relacion asignación que se desea crear
     * @throws IllegalArgumentException si los datos o las referencias no son
     * válidos
     */
    @Override
    public void crear(EmpleadoRol relacion) {

        validarRelacion(relacion);

        UUID idEmpleado
                = relacion.getIdEmpleado().getIdEmpleado();

        UUID idRol
                = relacion.getIdRol().getIdRol();

        Empleado empleado = em.find(
                Empleado.class,
                idEmpleado
        );

        Rol rol = em.find(
                Rol.class,
                idRol
        );

        if (empleado == null || rol == null) {
            throw new IllegalArgumentException(
                    "El empleado o el rol no existe");
        }

        if (!Boolean.TRUE.equals(empleado.getActivo())) {
            throw new IllegalArgumentException(
                    "No se pueden asignar roles a un empleado inactivo");
        }

        if (!Boolean.TRUE.equals(rol.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede asignar un rol inactivo");
        }

        if (em.find(
                EmpleadoRol.class,
                relacion.getIdEmpleadoRol()) != null) {

            throw new IllegalArgumentException(
                    "La asignación ya existe");
        }

        if (existeRelacion(idEmpleado, idRol)) {
            throw new IllegalArgumentException(
                    "El empleado ya tiene ese rol asignado");
        }

        relacion.setIdEmpleado(empleado);
        relacion.setIdRol(rol);

        em.persist(relacion);
        em.flush();
    }

    /**
     * Modifica estado y observaciones de una relación. No permite reasignar la
     * relación a otro empleado o rol mediante este método.
     */
    @Override
    public void modificar(EmpleadoRol relacion) {

        validarRelacion(relacion);

        EmpleadoRol actual = em.find(
                EmpleadoRol.class,
                relacion.getIdEmpleadoRol()
        );

        if (actual == null) {
            throw new IllegalArgumentException(
                    "La asignación no existe");
        }

        if (!actual.getIdEmpleado().getIdEmpleado()
                .equals(relacion.getIdEmpleado().getIdEmpleado())
                || !actual.getIdRol().getIdRol()
                        .equals(relacion.getIdRol().getIdRol())) {

            throw new IllegalArgumentException(
                    "No se puede cambiar el empleado "
                    + "o el rol de una asignación existente");
        }

        if (Boolean.TRUE.equals(relacion.getActivo())
                && (!Boolean.TRUE.equals(
                        actual.getIdEmpleado().getActivo())
                || !Boolean.TRUE.equals(
                        actual.getIdRol().getActivo()))) {

            throw new IllegalArgumentException(
                    "No se puede activar una relación "
                    + "con empleado o rol inactivo");
        }

        actual.setActivo(relacion.getActivo());
        actual.setObservaciones(relacion.getObservaciones());
    }

    /**
     * Quita una asignación si no está referenciada por órdenes o facturas.
     *
     * Nunca elimina registros históricos relacionados.
     */
    @Override
    public void eliminar(EmpleadoRol relacion) {

        if (relacion == null
                || relacion.getIdEmpleadoRol() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar una asignación válida");
        }

        EmpleadoRol actual = em.find(
                EmpleadoRol.class,
                relacion.getIdEmpleadoRol()
        );

        if (actual == null) {
            throw new IllegalArgumentException(
                    "La asignación ya no existe");
        }

        UUID id = actual.getIdEmpleadoRol();

        long ordenes = em.createNamedQuery(
                "EmpleadoRol.countOrdenes", Long.class
        )
                .setParameter("idEmpleadoRol", id)
                .getSingleResult();

        long facturas = em.createNamedQuery(
                "EmpleadoRol.countFacturas", Long.class
        )
                .setParameter("idEmpleadoRol", id)
                .getSingleResult();

        if (ordenes > 0 || facturas > 0) {
            throw new IllegalArgumentException(
                    "No se puede quitar este rol porque "
                    + "tiene órdenes o facturas asociadas");
        }

        em.remove(actual);
        em.flush();
    }

    /**
     * Comprueba que el identificador sea válido.
     */
    private void validarId(UUID id, String nombre) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El identificador de " + nombre
                    + " es obligatorio");
        }
    }

    /**
     * Comprueba referencias y datos obligatorios.
     */
    private void validarRelacion(EmpleadoRol relacion) {

        if (relacion == null) {
            throw new IllegalArgumentException(
                    "La asignación no puede ser nula");
        }

        validarId(relacion.getIdEmpleadoRol(), "asignación");

        if (relacion.getIdEmpleado() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un empleado");
        }

        if (relacion.getIdRol() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un rol");
        }

        validarId(
                relacion.getIdEmpleado().getIdEmpleado(),
                "empleado"
        );

        validarId(
                relacion.getIdRol().getIdRol(),
                "rol"
        );

        if (relacion.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado de la asignación");
        }
    }
}
