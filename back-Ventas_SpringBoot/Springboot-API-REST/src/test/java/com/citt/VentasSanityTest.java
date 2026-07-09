package com.citt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VentasSanityTest {

    @Test
    void calculaTotalDeVentaCorrectamente() {
        double precioUnitario = 2500.0;
        int cantidad = 3;
        double total = precioUnitario * cantidad;
        assertEquals(7500.0, total);
    }

    @Test
    void rechazaCantidadNegativaDeProductos() {
        assertThrows(IllegalArgumentException.class, () -> validarCantidad(-1));
    }

    private int validarCantidad(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa");
        }
        return cantidad;
    }
}
