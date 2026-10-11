package br.com.brasil_saas.fiscal.sped;

import br.com.swconsultoria.efd.icms.bo.GerarEfdIcms;
import br.com.swconsultoria.efd.icms.registros.EfdIcms;
import br.com.swconsultoria.efd.icms.registros.bloco0.Registro0000;
import br.com.swconsultoria.efd.icms.registros.bloco0.Registro0100;
import br.com.swconsultoria.efd.icms.registros.bloco0.Registro0150;
import br.com.swconsultoria.efd.icms.registros.bloco0.Registro0200;
import br.com.swconsultoria.efd.icms.registros.bloco0.Registro0206;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * Gera o arquivo SPED EFD ICMS/IPI.
 *
 * <h3>O que EFD e, e por que isso nao e integracao com SEFAZ</h3>
 * NFS-e e NFe se ENVIAM para um web service, que responde protocolo. EFD nao:
 * o arquivo e gerado localmente, assinado, e guardado. A SEFAZ ou a Receita
 * Federal vem BUSCAR depois. Nao ha autorizacao, nao ha protocolo, nao ha fila.
 * Por isso EFD e mais barato que MDF-e, e por isso da para validar inteiro
 * sem depender de ninguem la fora.
 *
 * <h3>O formato, e onde ele esta</h3>
 * Largura nao e fixa de verdade: o separador e o pipe, e o que tem espacaco a
 * esquerda e o numero de digitos. O gabarito esta em
 * {@code microservices/Java-Efd-Icms/src/test/resources/efd.txt}, 104 linhas de
 * um EFD completo. Foi ele que mostrou a ordem dos registros e o fechamento.
 *
 * <p>Registros, na ordem em que o arquivo tem que sair:
 * <pre>
 * 0000  cabecalho: cod_ver, cod_fin, competencia, emitente
 * 0001  informacoes adicionais
 * 0005  nome fantasia, endereco do emitente
 * 0100  contador de linhas do arquivo
 * 0150  participantes (cliente, fornecedor)
 * 0200  produto, com 0206 embutido para o NCM
 * C100  documentos fiscais (o bloco C, 84 registros na lib)
 * 9900  quantas linhas de cada registro -- o "dicionario" do arquivo
 * 9990  total de linhas do bloco 9
 * 9999  total de linhas do arquivo
 * </pre>
 *
 * <h3>O fechamento e a parte que faz a SEFAZ recusar</h3>
 * O 9900 declara quantas linhas de cada registro existem. Se a contagem
 * divergir do que foi realmente escrito, o arquivo e recusado sem apontar o
 * registro. Por isso {@link #montarContadores} conta de verdade, em vez de
 * receber o numero de fora.
 */
@Slf4j
@Service
public class SpedEfdIcmsService {

    /** Versao do layout. O 020 e o da serie 3, e o aceito hoje. */
    private static final String COD_VER = "020";

    /**
     * Gera o arquivo EFD a partir dos cabecalhos e dos documentos.
     *
     * @param efd o EFD montado pelo chamador
     * @return o conteudo do arquivo, com uma linha por registro
     */
    public String gerar(EfdIcms efd) {
        // DELEGADO INTEIRO PARA A LIB. Nao acrescento nada aqui.
        //
        // A primeira versao deste metodo acrescentava o bloco 0 na mao, com
        // `registro.toString()`, e depois chamava a lib. O resultado saiam
        // DUAS linhas de cada registro: uma certa, vinda da lib, e uma
        // `br.com...Registro0000@df63e539`, que e o toString() padrao do
        // objeto e nao o leiaute SPED. E o contador 9900 dobrava junto.
        //
        // Alem disso a lib ja monta o 9900, o 9990 e o 9999, e monta certo:
        // o 0990 do arquivo de referencia conta 14 linhas, que sao as do
        // cabecalho MAIS as do bloco C, porque o 0990 conta o bloco 0 inteiro
        // e o bloco 0 inclui as letras. Repor essa contagem aqui e chance de
        // errar o que a lib ja faz certo.
        StringBuilder sb = new StringBuilder();
        GerarEfdIcms.gerar(efd, sb);
        return sb.toString();
    }





    // ------------------------------------------------------------------ //
    // Constroi o cabecalho, que e o que a SEFAZ olha primeiro
    // ------------------------------------------------------------------ //

    /**
     * Monta o 0000. E o registro que diz quem esta falando e de que periodo,
     * e a SEFAZ recusa por ele antes de olhar qualquer outra coisa.
     *
     * @param competencia no formato MM/aaaa
     */
    public Registro0000 cabecalho(String competencia, String cnpj, String nome,
                                  String uf, String ie, String codMun,
                                  String im, String indPerfil, String indAtiv) {
        Registro0000 r = new Registro0000();
        r.setCod_ver(COD_VER);
        r.setCod_fin("0");               // 0 = normal
        r.setDt_ini(periodoInicio(competencia));
        r.setDt_fin(periodoFim(competencia));
        r.setNome(nome);
        r.setCnpj(somenteDigitos(cnpj));
        r.setUf(uf);
        r.setIe(ie);
        r.setCod_mun(codMun);
        r.setIm(im);
        r.setInd_perfil(indPerfil);
        r.setInd_ativ(indAtiv);
        return r;
    }

    public Registro0100 contadorEmitente(String nome, String cnpj, String cep,
                                         String endereco, String numero,
                                         String complemento, String bairro,
                                         String telefone, String email,
                                         String codMun) {
        Registro0100 r = new Registro0100();
        r.setNome(nome);
        r.setCnpj(somenteDigitos(cnpj));
        r.setCep(somenteDigitos(cep));
        r.setEnd(endereco);
        r.setNum(numero);
        r.setCompl(complemento);
        r.setBairro(bairro);
        r.setFone(somenteDigitos(telefone));
        r.setEmail(email);
        r.setCod_mun(codMun);
        return r;
    }

    /**
     * Monta o 0200 e o 0206 juntos.
     *
     * <p>O NCM nao vai direto no 0200: a lib espera o 0206 como objeto filho,
     * porque no arquivo ele sai na linha seguinte com o codigo da combustivel.
     * Deixar o 0206 nulo gera o 0200 sem NCM, e o arquivo passa pela
     * validacao de formato e e recusado por conteudo.
     */
    public Registro0200 produto(String codItem, String descricao, String unidade,
                                String ncm, String cest, String aliquotaIcms,
                                String codComb) {
        Registro0206 r0206 = new Registro0206();
        r0206.setCod_comb(codComb);

        Registro0200 r = new Registro0200();
        r.setCod_item(codItem);
        r.setDescr_item(descricao);
        r.setUnid_inv(unidade);
        r.setTipo_item("00");            // mercadoria para revenda
        r.setCod_ncm(somenteDigitos(ncm));
        r.setCest(somenteDigitos(cest));
        r.setAliq_icms(aliquotaIcms);
        r.setRegistro0206(r0206);
        return r;
    }

    public Registro0150 participante(String codPart, String nome, String cnpj,
                                     String cpf, String ie, String codMun) {
        Registro0150 r = new Registro0150();
        r.setCod_part(codPart);
        r.setNome(nome);
        r.setCnpj(somenteDigitos(cnpj));
        r.setCpf(somenteDigitos(cpf));
        r.setIe(ie);
        r.setCod_mun(codMun);
        return r;
    }

    // ------------------------------------------------------------------ //

    /** MM/aaaa -> ddMMyyyy do primeiro dia. */
    public String periodoInicio(String competencia) {
        return "01" + competencia.replace("/", "");
    }

    /** MM/aaaa -> ddMMyyyy do ultimo dia. */
    public String periodoFim(String competencia) {
        String c = competencia.replace("/", "");
        String mm = c.substring(0, 2);
        String aaaa = c.substring(2, 6);
        int ultimo = java.time.YearMonth.of(Integer.parseInt(aaaa),
                Integer.parseInt(mm)).lengthOfMonth();
        return String.format("%02d%s%s", ultimo, mm, aaaa);
    }

    public String somenteDigitos(String valor) {
        if (valor == null) return "";
        return valor.replaceAll("\\D", "");
    }
}
