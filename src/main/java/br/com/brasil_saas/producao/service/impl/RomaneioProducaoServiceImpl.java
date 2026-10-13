package br.com.brasil_saas.producao.service.impl;

import br.com.brasil_saas.producao.model.Producao;
import br.com.brasil_saas.producao.model.RomaneioProducao;
import br.com.brasil_saas.producao.model.RomaneioProducaoItem;
import br.com.brasil_saas.producao.repository.ProducaoRepository;
import br.com.brasil_saas.producao.repository.RomaneioProducaoRepository;
import br.com.brasil_saas.producao.service.RomaneioProducaoItemRequest;
import br.com.brasil_saas.producao.service.RomaneioProducaoRequest;
import br.com.brasil_saas.producao.service.RomaneioProducaoService;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RomaneioProducaoServiceImpl implements RomaneioProducaoService {

    private final RomaneioProducaoRepository repository;
    private final ProducaoRepository producaoRepository;

    @Override
    @Transactional
    public RomaneioProducao criar(Long empresaId, RomaneioProducaoRequest request) {
        if (request.numero() == null || request.numero().isBlank()) {
            throw new BusinessException("Número do romaneio é obrigatório");
        }
        if (request.producaoId() == null) {
            throw new BusinessException("Ordem de produção é obrigatória");
        }
        if (request.itens() == null || request.itens().isEmpty()) {
            throw new BusinessException("O romaneio deve possuir ao menos um item");
        }

        Producao producao = producaoRepository.findById(request.producaoId())
            .filter(p -> empresaId.equals(p.getEmpresaId()))
            .orElseThrow(() -> new BusinessException("Ordem de produção não encontrada para a empresa"));

        RomaneioProducao romaneio = new RomaneioProducao();
        romaneio.setEmpresaId(empresaId);
        romaneio.setNumero(request.numero().trim());
        romaneio.setProducao(producao);
        romaneio.setDataRomaneio(request.dataRomaneio() != null ? request.dataRomaneio() : LocalDate.now());
        romaneio.setDestino(request.destino());
        romaneio.setResponsavelId(request.responsavelId());
        romaneio.setVeiculoId(request.veiculoId());
        romaneio.setStatus("ABERTO");
        romaneio.setObservacoes(request.observacoes());

        List<RomaneioProducaoItem> itens = new ArrayList<>();
        for (RomaneioProducaoItemRequest itemRequest : request.itens()) {
            if (itemRequest.produtoId() == null || itemRequest.quantidade() == null || itemRequest.quantidade().signum() <= 0) {
                throw new BusinessException("Cada item deve possuir produto e quantidade maior que zero");
            }

            RomaneioProducaoItem item = new RomaneioProducaoItem();
            item.setRomaneio(romaneio);
            item.setProdutoId(itemRequest.produtoId());
            item.setDescricao(itemRequest.descricao());
            item.setQuantidade(itemRequest.quantidade());
            item.setUnidadeMedida(itemRequest.unidadeMedida() != null ? itemRequest.unidadeMedida() : "UN");
            item.setLote(itemRequest.lote());
            item.setObservacoes(itemRequest.observacoes());
            itens.add(item);
        }

        romaneio.setItens(itens);
        return repository.save(romaneio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RomaneioProducao> listar(Long empresaId) {
        List<RomaneioProducao> romaneios =
                repository.findByEmpresaIdAndDeletedAtIsNullOrderByDataRomaneioDesc(empresaId);
        for (RomaneioProducao r : romaneios) {
            if (r.getProducao() != null && r.getProducao().getItens() != null) {
                org.hibernate.Hibernate.initialize(r.getProducao().getItens());
            }
        }
        return romaneios;
    }

    private RomaneioProducao carregar(Long empresaId, Long id) {
        return repository.findById(id)
            .filter(r -> empresaId.equals(r.getEmpresaId()) && r.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException("Romaneio não encontrado"));
    }

    @Override
    @Transactional
    public RomaneioProducao conferir(Long empresaId, Long id) {
        RomaneioProducao r = carregar(empresaId, id);
        if (!"ABERTO".equals(r.getStatus()))
            throw new BusinessException("Somente romaneio ABERTO pode ser conferido (status=" + r.getStatus() + ")");
        r.setStatus("CONFERIDO");
        return repository.save(r);
    }

    @Override
    @Transactional
    public RomaneioProducao liberar(Long empresaId, Long id) {
        RomaneioProducao r = carregar(empresaId, id);
        if (!"CONFERIDO".equals(r.getStatus()))
            throw new BusinessException("Somente romaneio CONFERIDO pode ser liberado (status=" + r.getStatus() + ")");
        r.setStatus("LIBERADO");
        return repository.save(r);
    }

    @Override
    @Transactional
    public RomaneioProducao cancelar(Long empresaId, Long id) {
        RomaneioProducao r = carregar(empresaId, id);
        if (!"ABERTO".equals(r.getStatus()))
            throw new BusinessException("Somente romaneio ABERTO pode ser cancelado (status=" + r.getStatus() + ")");
        r.setStatus("CANCELADO");
        return repository.save(r);
    }
}
