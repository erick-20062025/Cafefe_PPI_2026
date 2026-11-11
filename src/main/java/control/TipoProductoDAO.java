/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package control;

import entity.TipoProducto;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import validation.TipoProductoValidator;

/**
 *
 * @author diegoli
 */
@Stateless
public class TipoProductoDAO extends DefaultDAO<TipoProducto> {

    @PersistenceContext(unitName = "my_persistence_unit")
    private EntityManager em;

    public TipoProductoDAO() {
        super(TipoProducto.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    public void crear(TipoProducto reg) {
        TipoProductoValidator.validar(reg);
        super.crear(reg);
    }

    @Override
    public void modificar(TipoProducto reg) {
        TipoProductoValidator.validar(reg);
        super.modificar(reg);
    }

}
