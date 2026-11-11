
package control;

import entity.Caja;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;

/**
 * DAO encargado de la persistencia de cajas.
 *
 * Utiliza consultas nombradas definidas en las entidades,
 * validaciones de negocio y transacciones EJB/JTA.
 *
 * @author erick
 */
@Stateless
public class CajaDAO extends DefaultDAO<Caja> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public CajaDAO() {
        super(Caja.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    /**
     * Obtiene todas las cajas registradas.
     *
     * @return lista de cajas; puede estar vacía
     */
    @Override
    public List<Caja> findAll() {
        return em.createNamedQuery(
                "Caja.findAll",
                Caja.class
        ).getResultList();
    }

    /**
     * Busca una caja mediante su UUID.
     *
     * @param id identificador de la caja
     * @return caja encontrada o null
     * @throws IllegalArgumentException si el ID es inválido
     */
    @Override
    public Caja findById(Object id) {

        if (!(id instanceof UUID)) {
            throw new IllegalArgumentException(
                    "El identificador de la caja no es válido");
        }

        return em.find(Caja.class, id);
    }

    /**
     * Crea una nueva caja.
     *
     * @param reg caja a registrar
     * @throws IllegalArgumentException si los datos son
     * inválidos o el identificador ya existe
     */
    @Override
    public void crear(Caja reg) {

        validarCaja(reg);

        if (em.find(Caja.class, reg.getIdCaja()) != null) {
            throw new IllegalArgumentException(
                    "Ya existe una caja con ese identificador");
        }

        em.persist(reg);
    }

    /**
     * Actualiza una caja existente.
     *
     * @param reg caja con los nuevos datos
     * @throws IllegalArgumentException si la caja
     * es inválida o no existe
     */
    @Override
    public void modificar(Caja reg) {

        validarCaja(reg);

        Caja existente = em.find(
                Caja.class,
                reg.getIdCaja()
        );

        if (existente == null) {
            throw new IllegalArgumentException(
                    "La caja que intenta modificar no existe");
        }

        em.merge(reg);
    }

    /**
     * Elimina una caja sin facturas asociadas.
     *
     * Nunca elimina facturas para permitir el borrado.
     * La base de datos conserva la responsabilidad
     * final de verificar la integridad referencial.
     *
     * @param reg caja seleccionada
     * @throws IllegalArgumentException si la caja
     * es inválida, inexistente o tiene facturas
     */
    @Override
    public void eliminar(Caja reg) {

        if (reg == null || reg.getIdCaja() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar una caja válida");
        }

        UUID idCaja = reg.getIdCaja();

        Caja administrada = em.find(Caja.class, idCaja);

        if (administrada == null) {
            throw new IllegalArgumentException(
                    "La caja ya no existe");
        }

        if (countFacturas(idCaja) > 0) {
            throw new IllegalArgumentException(
                    "No se puede eliminar una caja "
                    + "que tiene facturas asociadas");
        }

        em.remove(administrada);

        // Fuerza la verificación SQL antes de finalizar
        // la operación EJB, sin reemplazar el control
        // transaccional del contenedor.
        em.flush();
    }

    /**
     * Cuenta las facturas asociadas a una caja.
     *
     * @param idCaja identificador de la caja
     * @return cantidad de facturas asociadas
     * @throws IllegalArgumentException si el ID es nulo
     */
    public long countFacturas(UUID idCaja) {

        if (idCaja == null) {
            throw new IllegalArgumentException(
                    "El identificador de la caja es obligatorio");
        }

        return em.createNamedQuery(
                "Caja.countFacturas",
                Long.class
        )
        .setParameter("idCaja", idCaja)
        .getSingleResult();
    }

    /**
     * Valida los campos obligatorios de Caja.
     *
     * @param caja entidad a validar
     * @throws IllegalArgumentException si algún campo
     * no cumple con las reglas
     */
    private void validarCaja(Caja caja) {

        if (caja == null) {
            throw new IllegalArgumentException(
                    "La caja no puede ser nula");
        }

        if (caja.getIdCaja() == null) {
            throw new IllegalArgumentException(
                    "El identificador es obligatorio");
        }

        String nombre = caja.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre de la caja es obligatorio");
        }

        nombre = nombre.trim();

        if (nombre.length() < 2 || nombre.length() > 155) {
            throw new IllegalArgumentException(
                    "El nombre debe tener entre 2 y 155 caracteres");
        }

        if (caja.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado de la caja");
        }

        caja.setNombre(nombre);
    }
}
