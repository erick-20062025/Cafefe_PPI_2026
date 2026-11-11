package validation;

import entity.ProductoTipoProducto;
import entity.Producto;
import entity.TipoProducto;

public final class ProductoTipoProductoValidator {

    private ProductoTipoProductoValidator() {
    }

    public static void validar(ProductoTipoProducto relacion) {

        if (relacion == null) {
            throw new IllegalArgumentException(
                    "La relación producto-tipo no puede ser nula");
        }

        Producto producto = relacion.getProducto();

        if (producto == null || producto.getIdProducto() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un producto válido");
        }

        TipoProducto tipoProducto = relacion.getIdTipoProducto();

        if (tipoProducto == null
                || tipoProducto.getIdTipoProducto() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un tipo de producto válido");
        }

        if (!Boolean.TRUE.equals(tipoProducto.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede asignar un tipo de producto inactivo");
        }
    }
}
