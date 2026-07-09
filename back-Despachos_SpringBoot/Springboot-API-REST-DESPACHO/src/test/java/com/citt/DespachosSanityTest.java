package com.citt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DespachosSanityTest {

    @Test
    void calculaDiasEstimadosDeDespacho() {
        int diasBase = 2;
        int diasPorRegionExtra = 3;
        int totalDias = diasBase + diasPorRegionExtra;
        assertEquals(5, totalDias);
    }

    @Test
    void rechazaEstadoDeDespachoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> validarEstado("EN_ORBITA"));
    }

    private String validarEstado(String estado) {
        var estadosValidos = java.util.List.of("PENDIENTE", "EN_RUTA", "ENTREGADO");
        if (!estadosValidos.contains(estado)) {
            throw new IllegalArgumentException("Estado de despacho inválido: " + estado);
        }
        return estado;
    }
}
