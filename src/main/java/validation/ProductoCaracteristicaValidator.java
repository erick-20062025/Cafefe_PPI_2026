
package validation;

import entity.Caracteristica;
import entity.Producto;
import entity.ProductoCaracteristica;
import entity.TipoCaracteristica;
import java.util.regex.PatternSyntaxException;

public final class ProductoCaracteristicaValidator {

    private ProductoCaracteristicaValidator() {
    }

    public static void validar(ProductoCaracteristica relacion) {

        if (relacion == null) {
            throw new IllegalArgumentException(
                    "La relación producto-característica no puede ser nula");
        }

        Producto producto = relacion.getIdProducto();

        if (producto == null || producto.getIdProducto() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar un producto válido");
        }

        Caracteristica caracteristica = relacion.getIdCaracteristica();

        if (caracteristica == null
                || caracteristica.getIdCaracteristica() == null) {
            throw new IllegalArgumentException(
                    "Debe seleccionar una característica válida");
        }

        if (!Boolean.TRUE.equals(caracteristica.getActivo())) {
            throw new IllegalArgumentException(
                    "No se puede asignar una característica inactiva");
        }

        String valor = relacion.getValor();

        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Debe ingresar un valor para la característica");
        }

        TipoCaracteristica tipo =
                caracteristica.getIdTipoCaracteristica();

        if (tipo != null) {
            String expresion = tipo.getExpresionRegular();

            if (expresion != null && !expresion.isBlank()) {
                try {
                    if (!valor.matches(expresion)) {
                        throw new IllegalArgumentException(
                                "El valor ingresado no cumple con el formato requerido");
                    }
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException(
                            "La expresión regular configurada no es válida", e);
                }
            }
        }
    }
}
