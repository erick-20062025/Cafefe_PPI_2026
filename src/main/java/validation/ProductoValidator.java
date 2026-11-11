package validation;

import entity.Producto;
import java.math.BigDecimal;

public final class ProductoValidator {

    private ProductoValidator() {
    }

    public static void validar(Producto producto) {

        if (producto == null) {
            throw new IllegalArgumentException(
                    "El producto no puede ser nulo");
        }
        if (producto.getIdProducto() == null) {
            throw new IllegalArgumentException(
                    "El identificador del producto es obligatorio");
        }

        String nombre = producto.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe ingresar el nombre del producto");
        }

        if (nombre.length() > 155) {
            throw new IllegalArgumentException(
                    "El nombre del producto no puede superar 155 caracteres");
        }

        if (producto.getActivo() == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el estado del producto");
        }

        BigDecimal precio = producto.getPrecioSugerido();

        if (precio == null) {
            throw new IllegalArgumentException(
                    "Debe ingresar el precio sugerido del producto");
        }

        if (precio.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "El precio sugerido no puede ser negativo");
        }
    }
}
