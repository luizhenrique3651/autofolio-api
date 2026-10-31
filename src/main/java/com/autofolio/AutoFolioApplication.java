package com.autofolio;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutoFolioApplication {

    /*
     * Bloco estático para carregamento de variáveis de ambiente do arquivo .env.
     * 
     * O Spring Boot não lê arquivos .env nativamente. Ao usar um bloco estático, garantimos que
     * o Dotenv carregue as variáveis para o System.setProperty() ANTES que o contexto do Spring 
     * seja inicializado. Isso é fundamental para:
     * 
     * 1. Desenvolvimento Local: Carregar chaves de API e senhas sem precisar exportar no terminal.
     * 2. Testes de Integração: Garantir que o @SpringBootTest consiga resolver placeholders 
     *    como ${CLOUDINARY_API_KEY} mesmo quando o teste é disparado pela IDE ou Maven.
     * 
     * Nota: Em produção (VPS/Docker), as variáveis de ambiente reais do SO terão prioridade 
     * sobre o que estiver no arquivo .env local.
     */
    static {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    }

    public static void main(String[] args) {
        SpringApplication.run(AutoFolioApplication.class, args);
    }

}
