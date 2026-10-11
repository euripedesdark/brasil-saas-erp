package br.com.brasil_saas.fiscal.sped;

import br.com.swconsultoria.efd.icms.registros.EfdIcms;
import br.com.swconsultoria.efd.icms.registros.bloco0.Bloco0;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPED EFD ICMS/IPI.
 *
 * <p>EFD nao se envia para ninguem: o arquivo e gerado, assinado e guardado, e
 * quem vem buscar e a SEFAZ ou a Receita. Nao ha web service, protocolo nem
 * fila. Por isso este controller tem um unico endpoint que faz trabalho de
 * verdade, {@link #gerar}, e ele roda inteiro sem depender de nada externo.
 *
 * <p>O que ainda nao existe: ler os dados fiscais do ERP e montar o EFD a
 * partir deles. Hoje o cabecalho vem do corpo da requisicao, que e o que
 * permite validar o formato contra o gabarito da biblioteca. Ligar no banco
 * e o passo seguinte, e a tabela de registros do EFD ainda nao existe (o
 * {@code bc_fis_sped_fiscal} atual e um registro de arquivo gerado, com 4
 * campos, nao as linhas do EFD).
 */
@Tag(name = "Fiscal - SPED EFD ICMS/IPI")
@RestController
@RequestMapping("/api/fiscal/sped")
@RequiredArgsConstructor
public class SpedEfdController {

    private final SpedEfdIcmsService efd;
    private final EfdPeriodoService periodo;
    private final EfdContribService contrib;
    private final br.com.brasil_saas.fiscal.repository.SpedFiscalRepository histEfd;
    private final br.com.brasil_saas.fiscal.repository.SpedContribuicoesRepository histContrib;

    /**
     * Gera o arquivo EFD a partir de cabecalho, participantes e produtos.
     *
     * <p>Devolve o conteudo e tambem a contagem de linhas, porque o 9900 e o
     * 9999 do arquivo tem de bater com o que foi escrito. Um EFD com contador
     * errado e recusado sem apontar a linha, e descobrir isso depois da entrega
     * custa mais do que conferir aqui.
     */
    @Operation(summary = "Gera o arquivo EFD ICMS/IPI e devolve os contadores")
    @PostMapping("/efd/gerar")
    @PreAuthorize("hasAuthority('fiscal:sped:gerar')")
    public Map<String, Object> gerar(@RequestBody PedidoEfd pedido) {
        EfdIcms documento = new EfdIcms();
        Bloco0 b0 = new Bloco0();
        documento.setBloco0(b0);

        b0.setRegistro0000(efd.cabecalho(
                pedido.competencia(), pedido.cnpj(), pedido.nome(),
                pedido.uf(), pedido.ie(), pedido.codMun(),
                pedido.im(), pedido.indPerfil(), pedido.indAtiv()));

        if (pedido.cep() != null) {
            b0.setRegistro0100(efd.contadorEmitente(
                    pedido.nome(), pedido.cnpj(), pedido.cep(),
                    pedido.endereco(), pedido.numero(),
                    pedido.complemento(), pedido.bairro(),
                    pedido.telefone(), pedido.email(), pedido.codMun()));
        }

        if (pedido.produtos() != null) {
            for (var p : pedido.produtos()) {
                b0.getRegistro0200().add(efd.produto(
                        p.codigo(), p.descricao(), p.unidade(),
                        p.ncm(), p.cest(), p.aliquotaIcms(), p.codComb()));
            }
        }

        if (pedido.participantes() != null) {
            for (var p : pedido.participantes()) {
                b0.getRegistro0150().add(efd.participante(
                        p.codigo(), p.nome(), p.cnpj(), p.cpf(),
                        p.ie(), p.codMun()));
            }
        }

        String conteudo = efd.gerar(documento);
        List<String> linhas = List.of(conteudo.split("\\R"));

        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("contrato_sped", "contrato_sped");
        saida.put("sucesso", true);
        saida.put("competencia", pedido.competencia());
        saida.put("totalLinhas", linhas.size());
        saida.put("contagemPorRegistro", conta(linhas));
        saida.put("conteudo", conteudo);
        return saida;
    }

    /**
     * Gera um EFD de exemplo, com o gabarito da biblioteca.
     *
     * <p>Existe para ter o formato de referencia sem precisar montar o pedido.
     * O esperado e bater com
     * {@code microservices/Java-Efd-Icms/src/test/resources/efd.txt}, que e o
     * EFD real da biblioteca. Se divergir, o formato mudou e o contador vai
     * fazer a SEFAZ recusar.
     */
    @Operation(summary = "Gera o EFD do periodo a partir das NFe emitidas")
    @PostMapping("/efd/gerar-periodo")
    @PreAuthorize("hasAuthority('fiscal:sped:gerar')")
    public Map<String, Object> gerarPeriodo(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody PedidoEfd pedido) { return periodo.gerarPeriodo(u.getEmpresaId(), pedido); }

    @Operation(summary = "Gera um EFD de exemplo, para conferir o formato")
    @GetMapping("/efd/exemplo")
    @PreAuthorize("hasAuthority('fiscal:sped:leitura')")
    public Map<String, Object> exemplo() {
        PedidoEfd p = new PedidoEfd("01/2026",
                "99999999999999", "NOME", "GO",
                "999999999", "9999999", "", "A", "1",
                "75000000", "RUA TESTE", "999", "COMPLEMENTO", "BAIRRO",
                "9999999999", "teste@teste",
                List.of(new Produto("810101001", "ETANOL HIDRATADO COMBUSTIVEL-COMUM",
                        "LT", "22071090", "", "0", "")),
                List.of(new Participante("99999999999", "FORNECEDOR",
                        "99999999999999", "", "99999999", "9999999")));
        return gerar(p);
    }
    @Operation(summary = "Gera o EFD-Contribuicoes do periodo a partir das apuracoes de PIS/COFINS")
    @PostMapping("/efd-contribuicoes/gerar-periodo")
    @PreAuthorize("hasAuthority('fiscal:sped:gerar')")
    public Map<String, Object> gerarContrib(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody PedidoEfd pedido) { return contrib.gerarPeriodo(u.getEmpresaId(), pedido); }

    private Map<String, Integer> conta(List<String> linhas) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (String l : linhas) {
            String corpo = l.startsWith("|") ? l.substring(1) : l;
            int barra = corpo.indexOf('|');
            if (barra > 0) contagem.merge(corpo.substring(0, barra), 1, Integer::sum);
        }
        return contagem;
    }
    @Operation(summary = "Lista arquivos SPED gerados")
    @GetMapping("/historico")
    @PreAuthorize("hasAuthority('fiscal:sped:gerar')")
    public Map<String, Object> historico(@AuthenticationPrincipal AuthenticatedUser u) { Map<String, Object> m = new LinkedHashMap<>(); m.put("efd", histEfd.findByEmpresaIdOrderByCompetenciaDesc(u.getEmpresaId())); m.put("contribuicoes", histContrib.findByEmpresaIdOrderByCompetenciaDesc(u.getEmpresaId())); return m; }

    /** Corpo do pedido. Records, para nao ter 20 campos em classe. */
    public record PedidoEfd(
            String competencia, String cnpj, String nome, String uf, String ie,
            String codMun, String im, String indPerfil, String indAtiv,
            String cep, String endereco, String numero, String complemento,
            String bairro, String telefone, String email,
            List<Produto> produtos, List<Participante> participantes) {}

    public record Produto(String codigo, String descricao, String unidade,
                          String ncm, String cest, String aliquotaIcms,
                          String codComb) {}

    public record Participante(String codigo, String nome, String cnpj,
                               String cpf, String ie, String codMun) {}
}
