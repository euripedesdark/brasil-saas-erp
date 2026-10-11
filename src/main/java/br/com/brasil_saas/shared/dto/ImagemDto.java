package br.com.brasil_saas.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagemDto {
    private String url;
    private String tipoConteudo;
    private Long tamanho;
}