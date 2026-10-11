package br.com.brasil_saas.fiscal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Palavra-chave do cadastro fiscal: o índice que faz a busca achar o código
 * quando o usuário digita o nome do negócio, e não a classificação.
 *
 * <p>Nível 1 ({@code origem = 'descricao-oficial'}) vem dos tokens da própria
 * descrição oficial — derivado, sem invenção. Nível 2 ({@code origem = 'curado'})
 * é sinônimo de negócio, e a origem fica declarada linha a linha para que
 * curadoria nunca se confunda com dado oficial.
 *
 * <p>Palavra-chave não é dado fiscal: uma palavra errada faz a busca errar, não a
 * nota. Ainda assim a procedência é gravada, pelo mesmo motivo do
 * {@code bc_fis_regra_tributaria.origem}.
 */
@Entity
@Table(name = "bc_fis_palavra_chave")
@Getter
@Setter
public class PalavraChave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID uuid;

    /** {@code ncm}, {@code issqn} ou {@code cfop}. */
    @Column(nullable = false, length = 16)
    private String tabela;

    /** Código oficial, como está no cadastro: {@code 22030000}, {@code 01.07.01.000}. */
    @Column(nullable = false, length = 16)
    private String codigo;

    /** Já normalizada: minúscula, sem acento. O índice é sobre este campo. */
    @Column(nullable = false, length = 80)
    private String palavra;

    /** SMALLINT no banco: o peso vai de 1 a 10. Precisava ser Short, e nao
     * Integer, ou o `ddl-auto: validate` derruba o boot — que e' o que ele existe
     * para fazer. */
    @Column(nullable = false)
    private Short peso = 5;

    /** {@code descricao-oficial} ou {@code curado}. */
    @Column(length = 64)
    private String origem;

    public PalavraChave() {
    }

    public PalavraChave(final String tabela, final String codigo, final String palabra,
                        final short peso, final String origem) {
        this.tabela = tabela;
        this.codigo = codigo;
        this.palavra = palabra;
        this.peso = peso;
        this.origem = origem;
    }
}
