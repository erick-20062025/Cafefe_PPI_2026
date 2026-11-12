
package validation;

import entity.TipoDescuento;

public final class TipoDescuentoValidator {

    private TipoDescuentoValidator() {
    }

    public static void validar(TipoDescuento tipoDescuento) {

        if (tipoDescuento == null) {
            throw new IllegalArgumentException(
                    "El tipo de descuento no puede ser nulo");
        }

        if (tipoDescuento.getIdTipoDescuento() == null) {
            throw new IllegalArgumentException(
                    "El identificador del tipo de descuento es obligatorio");
        }

        String nombre = tipoDescuento.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe ingresar el nombre del tipo de descuento");
        }

        if (tipoDescuento.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado del tipo de descuento");
        }

        Integer maximo = tipoDescuento.getDescuentoMaximo();

        if (maximo == null) {
            throw new IllegalArgumentException(
                    "Debe ingresar el descuento máximo");
        }

        if (maximo < 0 || maximo > 100) {
            throw new IllegalArgumentException(
                    "El descuento máximo debe estar entre 0 y 100 %");
        }
    }
}
