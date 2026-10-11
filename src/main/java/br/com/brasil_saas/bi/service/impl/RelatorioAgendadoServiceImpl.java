package br.com.brasil_saas.bi.service.impl;

import br.com.brasil_saas.bi.dto.RelatorioAgendadoRequest;
import br.com.brasil_saas.bi.model.Relatorio;
import br.com.brasil_saas.bi.model.RelatorioAgendado;
import br.com.brasil_saas.bi.repository.RelatorioAgendadoRepository;
import br.com.brasil_saas.bi.repository.RelatorioRepository;
import br.com.brasil_saas.bi.service.RelatorioAgendadoService;
import br.com.brasil_saas.bi.service.RelatorioService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RelatorioAgendadoServiceImpl implements RelatorioAgendadoService {

    private final RelatorioAgendadoRepository relatorioAgendadoRepository;
    private final RelatorioRepository relatorioRepository;
    private final RelatorioService relatorioService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    @Transactional
    public RelatorioAgendado criar(Long empresaId, RelatorioAgendadoRequest request) {
        validarAgendamento(request);
        Relatorio relatorio = relatorioRepository.findById(request.relatorioId())
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));

        if (!relatorio.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio nao pertence a esta empresa");
        }

        RelatorioAgendado agendado = new RelatorioAgendado();
        agendado.setEmpresaId(empresaId);
        agendado.setRelatorio(relatorio);
        agendado.setNome(request.nome());
        agendado.setDescricao(request.descricao());
        agendado.setFrequencia(request.frequencia());
        agendado.setIntervaloDias(request.intervaloDias());
        agendado.setProximaExecucao(request.proximaExecucao());
        agendado.setAtivo(request.ativo());
        agendado.setEmailDestinatarios(request.emailDestinatarios());
        agendado.setFormato(request.formato());
        agendado.setParametros(objectToJson(request.parametros()));
        agendado.setDataCriacao(LocalDateTime.now());

        return relatorioAgendadoRepository.save(agendado);
    }

    @Override
    @Transactional
    public RelatorioAgendado atualizar(Long empresaId, Long id, RelatorioAgendadoRequest request) {
        validarAgendamento(request);
        RelatorioAgendado agendado = relatorioAgendadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio agendado nao encontrado"));

        if (!agendado.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio agendado nao pertence a esta empresa");
        }

        Relatorio relatorio = relatorioRepository.findById(request.relatorioId())
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));
        if (!relatorio.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio nao pertence a esta empresa");
        }

        agendado.setRelatorio(relatorio);
        agendado.setNome(request.nome());
        agendado.setDescricao(request.descricao());
        agendado.setFrequencia(request.frequencia());
        agendado.setIntervaloDias(request.intervaloDias());
        agendado.setProximaExecucao(request.proximaExecucao());
        agendado.setAtivo(request.ativo());
        agendado.setEmailDestinatarios(request.emailDestinatarios());
        agendado.setFormato(request.formato());
        agendado.setParametros(objectToJson(request.parametros()));

        return relatorioAgendadoRepository.save(agendado);
    }

    @Override
    @Transactional(readOnly = true)
    public RelatorioAgendado buscarPorId(Long empresaId, Long id) {
        RelatorioAgendado agendado = relatorioAgendadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio agendado nao encontrado"));

        if (!agendado.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio agendado nao pertence a esta empresa");
        }

        return agendado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelatorioAgendado> listarPorEmpresa(Long empresaId) {
        return relatorioAgendadoRepository.findAllActiveByEmpresaId(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelatorioAgendado> listarPendentes(Long empresaId) {
        return relatorioAgendadoRepository.findPendingExecutions(empresaId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelatorioAgendado> listarPorFrequencia(Long empresaId, String frequencia) {
        return relatorioAgendadoRepository.findByEmpresaIdAndFrequencia(empresaId, frequencia);
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        RelatorioAgendado agendado = relatorioAgendadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio agendado nao encontrado"));

        if (!agendado.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio agendado nao pertence a esta empresa");
        }

        relatorioAgendadoRepository.delete(agendado);
    }

    @Override
    @Transactional
    public void executarAgendado(Long empresaId, Long id) {
        RelatorioAgendado agendado = buscarPorId(empresaId, id);

        try {
            // Executar relatorio
            Map<String, Object> parametros = jsonToObject(agendado.getParametros());
            Map<String, Object> resultado = relatorioService.executarRelatorio(
                    empresaId, 
                    agendado.getRelatorio().getId(), 
                    parametros
            );

            if (agendado.getFormato() == null || agendado.getFormato().isBlank()) throw new IllegalArgumentException("Formato do relatório é obrigatório");
            // Gerar arquivo
            ByteArrayOutputStream output = switch (agendado.getFormato().toUpperCase()) {
                case "PDF" -> relatorioService.gerarPdf(empresaId, agendado.getRelatorio().getId(), parametros);
                case "EXCEL" -> relatorioService.gerarExcel(empresaId, agendado.getRelatorio().getId(), parametros);
                case "CSV" -> relatorioService.gerarCsv(empresaId, agendado.getRelatorio().getId(), parametros);
                default -> null;
            };

            // Enviar por email
            if (output != null && agendado.getEmailDestinatarios() != null && !agendado.getEmailDestinatarios().isBlank()) {
                enviarEmail(agendado, output);
            }

            // Atualizar data de execucao
            agendado.setUltimaExecucao(LocalDateTime.now());
            agendado.setProximaExecucao(calcularProximaExecucao(agendado));
            relatorioAgendadoRepository.save(agendado);

        } catch (Exception e) {
            System.err.println("Erro ao executar relatorio agendado ID " + id + ": " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void definirProximaExecucao(Long empresaId, Long id, LocalDateTime proximaExecucao) {
        RelatorioAgendado agendado = buscarPorId(empresaId, id);
        if (proximaExecucao == null) {
            throw new IllegalArgumentException("A proxima execucao e obrigatoria");
        }
        agendado.setProximaExecucao(proximaExecucao);
        relatorioAgendadoRepository.save(agendado);
    }

    @Override
    @Transactional
    public void agendarProximaExecucao(Long empresaId, Long id) {
        RelatorioAgendado agendado = buscarPorId(empresaId, id);
        LocalDateTime proxima = calcularProximaExecucao(agendado);
        agendado.setProximaExecucao(proxima);
        relatorioAgendadoRepository.save(agendado);
    }

    // Executa automaticamente a cada hora
    @Scheduled(fixedRate = 3600000) // 1 hora = 3600000 ms
    @Transactional
    public void executarPendentes() {
        List<RelatorioAgendado> pendentes = relatorioAgendadoRepository.findAllPendingExecutions(LocalDateTime.now());
        
        for (RelatorioAgendado agendado : pendentes) {
            try {
                executarAgendado(agendado.getEmpresaId(), agendado.getId());
            } catch (Exception e) {
                System.err.println("Erro ao executar relatorio agendado ID " + agendado.getId() + ": " + e.getMessage());
            }
        }
    }

    private LocalDateTime calcularProximaExecucao(RelatorioAgendado agendado) {
        LocalDateTime agora = LocalDateTime.now();

        return switch (agendado.getFrequencia().toUpperCase()) {
            case "DIARIO" -> agora.plusDays(1);
            case "SEMANAL" -> agora.plusWeeks(1);
            case "QUINZENAL" -> agora.plusWeeks(2);
            case "MENSAL" -> agora.plusMonths(1);
            case "TRIMESTRAL" -> agora.plusMonths(3);
            case "ANUAL" -> agora.plusYears(1);
            case "CUSTOM" -> agora.plusDays(agendado.getIntervaloDias() != null ? agendado.getIntervaloDias() : 1);
            default -> agora.plusDays(1);
        };
    }

    private void enviarEmail(RelatorioAgendado agendado, ByteArrayOutputStream output) {
        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) throw new IllegalStateException("SMTP nao configurado para enviar relatorios");
            String[] emails = Arrays.stream(agendado.getEmailDestinatarios().split(","))
                    .map(String::trim).filter(s -> !s.isBlank()).toArray(String[]::new);
            if (emails.length == 0) throw new IllegalArgumentException("Nenhum destinatário válido");
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(emails);
            helper.setSubject("Relatório: " + agendado.getNome());
            helper.setText("Segue o relatório agendado: " + Objects.toString(agendado.getDescricao(), ""), false);
            String extensao = "EXCEL".equalsIgnoreCase(agendado.getFormato()) ? "xlsx"
                    : agendado.getFormato().toLowerCase(Locale.ROOT);
            helper.addAttachment("relatorio-" + agendado.getId() + "." + extensao,
                    new org.springframework.core.io.ByteArrayResource(output.toByteArray()));
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao enviar relatório agendado por e-mail", e);
        }
    }

    private void validarAgendamento(RelatorioAgendadoRequest request) {
        if (request == null) throw new IllegalArgumentException("Dados do agendamento são obrigatórios");
        if (request.relatorioId() == null) throw new IllegalArgumentException("Relatório é obrigatório");
        if (request.nome() == null || request.nome().isBlank()) throw new IllegalArgumentException("Nome do agendamento é obrigatório");
        if (request.frequencia() == null || request.frequencia().isBlank()) throw new IllegalArgumentException("Frequência é obrigatória");
        String frequencia = request.frequencia().toUpperCase(Locale.ROOT);
        Set<String> permitidas = Set.of("DIARIO","SEMANAL","QUINZENAL","MENSAL","TRIMESTRAL","ANUAL","CUSTOM");
        if (!permitidas.contains(frequencia)) throw new IllegalArgumentException("Frequência inválida: " + request.frequencia());
        if ("CUSTOM".equals(frequencia) && (request.intervaloDias() == null || request.intervaloDias() <= 0)) throw new IllegalArgumentException("Intervalo CUSTOM deve ser maior que zero");
        if (request.proximaExecucao() != null && request.proximaExecucao().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("Próxima execução não pode estar no passado");
        if (request.formato() == null || !Set.of("PDF","EXCEL","CSV").contains(request.formato().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Formato deve ser PDF, EXCEL ou CSV");
        if (request.ativo() && (request.emailDestinatarios() == null || request.emailDestinatarios().isBlank())) throw new IllegalArgumentException("Agendamento ativo exige destinatários de e-mail");
    }

    private String objectToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> jsonToObject(String json) {
        // Implementacao simplificada - em producao, usar Jackson/ObjectMapper
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isBlank() || !json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        // Remover chaves
        String content = json.substring(1, json.length() - 1);
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].replace("\"", "").trim();
                String value = keyValue[1].replace("\"", "").trim();
                result.put(key, value);
            }
        }
        return result;
    }
}
