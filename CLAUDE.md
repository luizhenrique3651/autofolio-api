# CLAUDE.md

@AGENTS.md

O arquivo acima é o guia canônico para qualquer agente neste repositório (discovery progressivo, ADRs, comandos, processo e armadilhas). Este arquivo só acrescenta o que é específico do Claude Code.

## Específico do Claude Code

- **Contexto sob demanda:** não use `@` para importar ADRs, README ou backlog aqui. Leia a seção necessária com a ferramenta de leitura quando a tarefa pedir (camadas 1 a 4 do discovery progressivo). Importar tudo gastaria contexto em toda conversa.
- **Busca ampla:** para varrer muitos arquivos atrás de um padrão, prefira `grep`/`find` ou um subagente de exploração, e traga só a conclusão para o contexto principal.
- **Bancada Egeon:** quando este terminal fizer parte de uma bancada (comando `egeon` disponível), o papel e as regras do terminal valem junto com este arquivo. Em conflito, a regra mais restritiva vence, e as ADRs continuam obrigatórias.
- **Memória:** decisões e preferências do dono que forem duráveis devem virar ADR, emenda de ADR ou item do backlog. A memória do Claude é complemento, não fonte da verdade.
- **Permissões:** comandos destrutivos (apagar arquivos fora do escopo, `git reset`, `git push`, mexer no `.env` ou em credenciais) não fazem parte de nenhuma tarefa. Peça ao dono.
