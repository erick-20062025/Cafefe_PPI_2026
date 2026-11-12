
package validation;

import entity.DescuentoProducto;

public final class DescuentoProductoValidator {

    private DescuentoProductoValidator() {
    }

    public static void validar(DescuentoProducto relacion) {

        if (relacion == null
                || relacion.getIdDescuentoProducto() == null) {
            throw new IllegalArgumentException(
                    "La asociación debe tener un identificador");
        }

        if (relacion.getIdProducto() == null
                || relacion.getIdProducto().getIdProducto() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un producto válido");
        }

        if (relacion.getIdDescuento() == null
                || relacion.getIdDescuento().getIdDescuento() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un descuento válido");
        }

        if (relacion.getValor() == null
                || relacion.getValor() <= 0) {
            throw new IllegalArgumentException(
                    "El descuento debe ser mayor que 0 %");
        }

        if (relacion.getValor() > 100) {
            throw new IllegalArgumentException(
                    "El descuento no puede superar 100 %");
        }

        if (relacion.getFechaDesde() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar la fecha desde");
        }
    }
}
