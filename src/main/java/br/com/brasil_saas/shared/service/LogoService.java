package br.com.brasil_saas.shared.service;

import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.cadastro.repository.FornecedorRepository;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.shared.image.ImagemDocumento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * Service para obter logos e fotos para uso em relatórios, notas fiscais e ordens de serviço
 */
@Service
@RequiredArgsConstructor
public class LogoService {

    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final FornecedorRepository fornecedorRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final GenericoImagemService imagemService;

    /**
     * Obter logo da empresa
     */
    public byte[] obterLogoEmpresa(Long empresaId) {
        if (!empresaRepository.existsById(empresaId)) {
            return null;
        }
        ImagemDocumento imagem = imagemService.buscarImagem(empresaId, "empresa_logo", empresaId);
        return imagem != null ? imagem.getConteudo() : null;
    }

    /**
     * Obter logo do cliente
     */
    public byte[] obterLogoCliente(Long empresaId, Long clienteId) {
        if (clienteRepository.findById(clienteId).filter(c -> empresaId.equals(c.getEmpresaId())).isEmpty()) {
            return null;
        }
        ImagemDocumento imagem = imagemService.buscarImagem(empresaId, "cliente_logo", clienteId);
        return imagem != null ? imagem.getConteudo() : null;
    }

    /**
     * Obter logo do fornecedor
     */
    public byte[] obterLogoFornecedor(Long empresaId, Long fornecedorId) {
        if (fornecedorRepository.findById(fornecedorId).filter(f -> empresaId.equals(f.getEmpresaId())).isEmpty()) {
            return null;
        }
        ImagemDocumento imagem = imagemService.buscarImagem(empresaId, "fornecedor_logo", fornecedorId);
        return imagem != null ? imagem.getConteudo() : null;
    }

    /**
     * Obter foto do funcionário
     */
    public byte[] obterFotoFuncionario(Long empresaId, Long funcionarioId) {
        if (funcionarioRepository.findById(funcionarioId).filter(f -> empresaId.equals(f.getEmpresaId())).isEmpty()) {
            return null;
        }
        ImagemDocumento imagem = imagemService.buscarImagem(empresaId, "funcionario_foto", funcionarioId);
        return imagem != null ? imagem.getConteudo() : null;
    }
}