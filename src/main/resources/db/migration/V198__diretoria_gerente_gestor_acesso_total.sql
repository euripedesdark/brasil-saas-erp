-- Garante acesso total para os perfis de gestão (DIRETORIA, GERENTE e GESTOR):
-- concessão de TODAS as permissões existentes, por empresa, para que os
-- administradores de negócio não dependam de grants manuais por módulo.
-- ADMIN e SUPERUSER já possuem o conjunto completo via migrations anteriores.
SET search_path TO brasil_saas, public;

INSERT INTO bc_core_perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id
  FROM bc_core_perfil p
 CROSS JOIN bc_core_permissao pe
 WHERE p.nome IN ('DIRETORIA', 'GERENTE', 'GESTOR')
   AND NOT EXISTS (
        SELECT 1 FROM bc_core_perfil_permissao pp
        WHERE pp.perfil_id = p.id AND pp.permissao_id = pe.id
   );
