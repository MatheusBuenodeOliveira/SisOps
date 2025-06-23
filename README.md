Nomes dos integrantes:
- Matheus Bueno de Oliveira
- João Pedro Kriger
- João Miguel Meier

-------------------------------------------------
Seção Implementação
-------------------------------------------------
O sistema implementa todas as características solicitadas no enunciado:

- Aceita submissão contínua de processos via console.
- Escalonamento contínuo de processos.
- Bloqueio de processos por IO e page fault.
- Retomada de processos após IO ou carregamento de página.
- Exibição do conteúdo da memória e tabela de páginas.
- Implementação de vitimação de página (FIFO).

Restrições:
- Não foram identificadas restrições críticas até o momento.
- Caso algum comando inválido seja digitado, o sistema exibe mensagem de erro e continua normalmente.
- O programa NOP do Kernel ocupa 1 página de memória, e o sistema não permite que o usuário o remova. Sendo assim, dependendo do processo deve ser calculado o tamanho da memória = NOP + 2 *  N processos.

-------------------------------------------------
Seção Testes
-------------------------------------------------

1. **Submissão e Execução Contínua de Processos**
   - Como executar: No prompt `SisOps>`, digite `exec <nome_do_programa>` ou `new <nome_do_programa>` várias vezes.
   - Resultado esperado: Novos processos são criados, atribuídos PIDs, e aparecem na lista de processos (`ps`). O escalonador alterna entre eles.

2. **Bloqueio por IO**
   - Como executar: Execute um processo que realiza operação de IO. Quando solicitado, digite `IO <pid> <valor>`.
   - Resultado esperado: O processo entra em estado bloqueado. A CPU continua executando outros processos. Após o comando IO, o processo retorna ao estado pronto e continua sua execução.

3. **Bloqueio por Page Fault**
   - Como executar: Execute um processo que acesse páginas não carregadas. O sistema deve bloquear o processo automaticamente.
   - Resultado esperado: O processo fica bloqueado até que a página seja carregada. Outros processos continuam executando. Após o carregamento, o processo retoma.

4. **Exibição do Estado da Memória**
   - Aparece conforme os retornos de Page Fault.

5. **Exibição da Tabela de Páginas**
   - Aparece conforme os retornos de Page Fault.

6. **Vitimação de Página**
   - Como executar: Execute processos até que a memória fique cheia e ocorra substituição de páginas.
   - Resultado esperado: O sistema realiza a vitimação de páginas conforme a política implementada, liberando espaço para novas páginas.

7. **Iniciar o Escalonador Manualmente (hacf)**
   - Como executar: Digite `hacf` no prompt.
   - Resultado esperado: O sistema inicia (ou reinicia) a thread de escalonamento de processos, permitindo que os processos sejam alternados automaticamente pela CPU.

8. **Parar o Escalonador Manualmente (schkill)**
   - Como executar: Digite `schkill` no prompt.
   - Resultado esperado: O sistema interrompe a thread de escalonamento, parando a alternância automática entre processos.
...
-------------------------------------------------
Observações
-------------------------------------------------
- Para ver todos os comandos disponíveis, digite `help`.
- Comandos especiais:
    - `hacf`: Inicia o escalonador de processos.
    - `schkill`: Para o escalonador de processos.
- Para encerrar o sistema, digite `exit`.
- O sistema imprime mensagens informativas a cada operação relevante.
