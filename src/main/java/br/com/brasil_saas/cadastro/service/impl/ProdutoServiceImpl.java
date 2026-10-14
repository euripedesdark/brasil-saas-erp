package br.com.brasil_saas.cadastro.service.impl;

import br.com.brasil_saas.cadastro.dto.*;
import br.com.brasil_saas.cadastro.model.*;
import br.com.brasil_saas.cadastro.repository.*;
import br.com.brasil_saas.cadastro.service.ProdutoService;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoServiceImpl implements ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoVariacaoRepository variacaoRepository;
    private final ProdutoKitRepository kitRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final UnidadeMedidaRepository unidadeMedidaRepository;

    @Override
    @Transactional
    public ProdutoResponse criar(ProdutoRequest request, Long empresaId) {
        // Mesma falha que a Pessoa: sem a empresa o INSERT viola o
        // not-null e volta 409, que parece duplicidade.
        request = request.comEmpresaDa(empresaId);
        if (produtoRepository.existsByEmpresaIdAndCodigoAndDeletedAtIsNull(empresaId, request.codigo())) {
            throw new BusinessException("CODIGO_DUPLICADO", "Código de produto já cadastrado");
        }

        Produto produto = new Produto();
        // Sem isto o INSERT viola o not-null de empresa_id e volta 409, que
        // parece duplicidade de codigo. Mesmo bug que a Pessoa tinha.
        produto.setEmpresaId(empresaId);
        mapearCampos(produto, request);

        if (request.categoriaId() != null) {
            categoriaRepository.findById(request.categoriaId())
                .ifPresentOrElse(produto::setCategoria, () -> {
                    throw new ResourceNotFoundException("Categoria não encontrada");
                });
        }
        if (request.marcaId() != null) {
            marcaRepository.findById(request.marcaId())
                .ifPresentOrElse(produto::setMarca, () -> {
                    throw new ResourceNotFoundException("Marca não encontrada");
                });
        }
        if (request.unidadeMedidaId() != null) {
            unidadeMedidaRepository.findById(request.unidadeMedidaId())
                .ifPresentOrElse(produto::setUnidadeMedida, () -> {
                    throw new ResourceNotFoundException("Unidade de medida não encontrada");
                });
        }

        if (request.variacoes() != null) {
            List<ProdutoVariacao> variacoes = new ArrayList<>();
            for (ProdutoVariacaoRequest vr : request.variacoes()) {
                ProdutoVariacao v = new ProdutoVariacao();
                v.setProduto(produto);
                v.setNome(vr.nome());
                v.setSku(vr.sku());
                v.setCodigoBarras(vr.codigoBarras());
                v.setPreco(vr.preco());
                v.setAtivo(vr.ativo() != null ? vr.ativo() : true);
                variacoes.add(v);
            }
            produto.setVariacoes(variacoes);
        }

        Produto saved = produtoRepository.save(produto);
        return ProdutoResponse.from(saved);
    }

    @Override
    @Transactional
    public ProdutoResponse atualizar(Long empresaId, Long id, ProdutoRequest request) {
        Produto produto = produtoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
            .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        if (produtoRepository.existsByEmpresaIdAndCodigoAndIdNotAndDeletedAtIsNull(empresaId, request.codigo(), id)) {
            throw new BusinessException("CODIGO_DUPLICADO", "Código de produto já cadastrado");
        }

        mapearCampos(produto, request);

        Produto saved = produtoRepository.save(produto);
        return ProdutoResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorId(Long empresaId, Long id) {
        Produto produto = produtoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
            .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return ProdutoResponse.from(produto);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProdutoResponse> listar(Long empresaId, String nome, String codigo, Long categoriaId, Long marcaId, Boolean ativo, Pageable pageable) {
        if (nome != null && nome.equals(codigo)) {
            Page<Produto> geral = produtoRepository.buscaGeral(
                    empresaId, nome, categoriaId, marcaId, ativo, pageable);
            return PageResponse.from(geral, ProdutoResponse::from);
        }
        Page<Produto> page = produtoRepository.buscar(empresaId, nome, codigo, categoriaId, marcaId, ativo, pageable);
        return PageResponse.from(page, ProdutoResponse::from);
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        Produto produto = produtoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
            .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setDeletedAt(LocalDateTime.now());
        produtoRepository.save(produto);
    }

    private void mapearCampos(Produto produto, ProdutoRequest request) {
        produto.setCodigo(request.codigo());
        produto.setNome(request.nome());
        produto.setDescricao(request.descricao());
        produto.setUrlProduto(request.urlProduto());
        produto.setNcm(request.ncm());
        produto.setCfopPadrao(request.cfopPadrao());
        // O CEST segue o produto e nao a nota: se o item veio com CEST 1300201,
        // e o mesmo nas proximas 500 compras. Guardar so em bc_fis_nfe_item
        // obrigaria a redigitar no cadastro toda vez, e a tela de produto nao
        // tinha o campo.
        produto.setCest(request.cest());
        produto.setCodigoBarras(request.codigoBarras());
        // Quatro colunas sao NOT NULL em bc_cad_produto, e a entidade declara
        // default ZERO para as duas de estoque. O service sobrescrevia esse
        // default com o null do request, entao qualquer cliente que omisse o
        // campo levaria
        //   null value in column "estoque_minimo" of relation "bc_cad_produto"
        // traduzido pelo handler para 409 "duplicidade ou FK invalida", que e
        // a mensagem errada. A tela manda `?? 0` e por isso nunca viu; a API,
        // sim.
        //
        // Este e o MESMO defeito que travou a entrada de nota (bc_fis_nfe com
        // valor_produtos null, bc_fis_nfe_item com created_at null), e a
        // terceira vez no mesmo dia. O padrao: coluna NOT NULL na tabela, valor
        // obrigatorio so na aplicacao, e o banco reclaimed no insert.
        produto.setPrecoCusto(valorOuZero(request.precoCusto()));
        produto.setPrecoVenda(valorOuZero(request.precoVenda()));
        produto.setEstoqueMinimo(valorOuZero(request.estoqueMinimo()));
        produto.setEstoqueMaximo(valorOuZero(request.estoqueMaximo()));
        produto.setPeso(request.peso());
        produto.setTipo(request.tipo() != null ? request.tipo() : "PRODUTO");
        produto.setAtivo(request.ativo() != null ? request.ativo() : true);
    }

    /**
     * BigDecimal que nunca e null.
     *
     * <p>Coluna NOT NULL com valor obrigatorio so na aplicacao e o tipo de
     * defeito que devolve 409 com mensagem de "duplicidade ou FK invalida" e
     * nao diz o que aconteceu. Ver o comentario em mapearCampos.
     */
    private BigDecimal valorOuZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
