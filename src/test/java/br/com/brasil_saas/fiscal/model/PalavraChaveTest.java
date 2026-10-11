package br.com.brasil_saas.fiscal.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PalavraChaveTest {
    @Test void constructorStoresTheSuppliedKeyword() {
        PalavraChave keyword = new PalavraChave("ncm", "22030000", "cerveja", (short)5, "curado");
        assertEquals("cerveja", keyword.getPalavra());
    }
}
