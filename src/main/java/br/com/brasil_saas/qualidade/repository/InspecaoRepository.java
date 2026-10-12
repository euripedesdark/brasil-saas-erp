package br.com.brasil_saas.qualidade.repository;
import br.com.brasil_saas.qualidade.model.Inspecao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InspecaoRepository extends JpaRepository<Inspecao,Long> {
    List<Inspecao> findAllByEmpresaIdAndDeletedAtIsNullOrderByDataInspecaoDesc(Long empresaId);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
