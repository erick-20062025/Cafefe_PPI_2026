
package validation;

import entity.TipoProducto;

public final class TipoProductoValidator {

    private TipoProductoValidator() {
    }

    public static void validar(TipoProducto tipoProducto) {

        if (tipoProducto == null) {
            throw new IllegalArgumentException(
                    "El tipo de producto no puede ser nulo");
        }

        String nombre = tipoProducto.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe ingresar el nombre del tipo de producto");
        }

        if (nombre.length() > 155) {
            throw new IllegalArgumentException(
                    "El nombre del tipo de producto no puede superar 155 caracteres");
        }

        if (tipoProducto.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado del tipo de producto");
        }
    }
}
