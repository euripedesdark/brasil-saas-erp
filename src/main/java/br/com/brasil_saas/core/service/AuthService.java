package br.com.brasil_saas.core.service;

import br.com.brasil_saas.core.service.dto.LoginRequest;
import br.com.brasil_saas.core.service.dto.LoginResponse;
import br.com.brasil_saas.core.service.dto.UserProfileResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse loginBanco(LoginRequest request);
    LoginResponse refresh(String refreshToken);
    UserProfileResponse me(Long usuarioId);

    /**
     * Entra sem senha, a partir de um principal já validado por tíquete SPNEGO.
     *
     * <p>A prova da identidade foi feita antes, no
     * {@code SpnegoService}, contra o keytab. Aqui não se confia em nenhum
     * header: o que chega é o nome que o KDC devolveu, e o método o procura no
     * ERP como se fosse o nome digitado na tela.
     *
     * <p>Se a pessoa existe no AD mas não tem linha em {@code bc_core_usuario},
     * o login falha com "não encontrado" — que é o correto. O AD diz quem a
     * pessoa é; quem guarda id, empresa e permissões é o ERP. Faltando o
     * espelho, não há o que usar, e inventar uma linha aqui criaria um cadastro
     * sem perfil e sem empresa.
     *
     * @param principal nome de usuário, já normalizado (sem {@code DOMINIO\})
     * @throws br.com.brasil_saas.shared.exception.BusinessException se não houver usuário ERP com esse nome
     */
    LoginResponse loginPorPrincipal(String principal);
}
