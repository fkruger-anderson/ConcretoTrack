# Plano de testes — entregas de concreto

Este documento diz **o que testar, como e em que ordem** nos dois apps (`:app` do motorista e
`:gerencial`).

**Situação:** as fases 1 e 2 estão feitas (85 testes unitários, em `app/src/test` e
`gerencial/src/test`). A fase 1 revelou um bug de arredondamento no saldo, já corrigido. As fases 3
a 6 ainda não foram feitas.

## 1. Os três tipos de teste (explicação rápida)

| Tipo | O que é | Onde roda | Velocidade |
|---|---|---|---|
| **Unitário** | Testa um pedaço pequeno de código sozinho (uma função, uma classe), sem internet, sem banco, sem tela. | No computador (JVM), pasta `src/test` | Milissegundos |
| **Integração** | Testa várias partes funcionando juntas: o código + o Room de verdade, ou o código + um Firestore de verdade (local, de mentirinha). | Emulador ou celular, pasta `src/androidTest` | Segundos |
| **Manual (campo)** | Uma pessoa segue um roteiro de passos no celular e confere o resultado. É o único jeito confiável de testar GPS e serviço em segundo plano. | Celular real | Minutos |

A regra prática: **quanto mais regra de negócio, mais teste unitário**. Os unitários são baratos, e
é neles que o saldo, o status do pedido, o rateio de km e a validação de CPF/CNPJ devem ser
conferidos.

Comandos (no Windows, na pasta do projeto):

```
gradlew.bat test                    (todos os unitários dos dois módulos)
gradlew.bat :gerencial:test         (só os unitários do gerencial)
gradlew.bat connectedAndroidTest    (instrumentados — precisa de celular/emulador conectado)
```

## 2. O que atrapalha testar hoje (e como resolver)

Antes de listar os casos, é importante saber por que parte do código **não dá** pra testar do
jeito que está:

1. **Os repositórios são `object` que chamam o Firebase direto** (`Firebase.firestore`). Um
   ViewModel que chama `ViagemRepositorio.atribuir(...)` sempre vai tentar falar com o Firebase de
   verdade — não tem como trocar por um "repositório falso" no teste.
   - **Solução leve (recomendada primeiro):** tirar as contas de dentro dos repositórios e
     ViewModels e colocar em **funções puras** (recebem dados, devolvem resultado, não acessam
     nada). O projeto já faz isso em `PedidoRepositorio.calcularStatus` — é o modelo a seguir.
     Candidatas: rateio de km (hoje dentro de `RoteiroRepositorio.encerrarRoteiro`), distância
     Haversine (hoje `private` em `PontoGpsRepositorio`), a decisão do botão da tela de detalhes
     (`DetalhesViagemViewModel.decidirAcao`) e as regras `podeIniciar`/`podeEncerrar` do
     `RoteiroViewModel`.
   - **Solução completa (depois):** cada repositório vira uma `interface` e o ViewModel recebe o
     repositório no construtor (via `ViewModelProvider.Factory`). No teste, passa-se uma versão
     falsa que devolve dados prontos.
2. **`android.util.Log` e `android.util.Patterns` dentro de ViewModels.** No teste unitário, as
   classes do Android são "cascas vazias": `Log.w` lança erro e `Patterns.EMAIL_ADDRESS` vem nulo.
   - `Patterns`: **resolvido** na fase 2. Os cadastros de motorista e de gerente passaram a usar
     `EmailUtils` (gerencial, pacote `dados`), com a mesma regra do Android, que funciona também no
     computador.
   - `Log`: os testes da fase 2 não passam por nenhum `Log.w`. Se um teste futuro passar, basta
     ligar `testOptions { unitTests.isReturnDefaultValues = true }` no `build.gradle.kts`.
3. **`LiveData` e `viewModelScope` precisam de ajuda no teste**: sem ela, o `LiveData` reclama que
   não está na thread principal e as coroutines não rodam. O `LiveData` foi **resolvido** na fase 2
   com a `InstantTaskExecutorRule`. As coroutines só vão precisar de ajuda quando algum teste passar
   da validação e chegar ao `viewModelScope.launch`.
4. **`formatarM3`/`formatarKm` dependem do idioma do aparelho** (`Locale.getDefault()`): o teste
   precisa fixar `pt-BR` antes, senão o resultado muda de máquina para máquina ("6,5" ou "6.5").
5. **As regras de segurança do Firestore não estão no repositório** (só no console do Firebase).
   Para testar com o emulador local, é preciso exportá-las para um arquivo `firestore.rules`.

### Dependências

| Dependência | Para quê | Situação |
|---|---|---|
| `testImplementation(libs.androidx.arch.core.testing)` (`androidx.arch.core:core-testing:2.2.0`) | `InstantTaskExecutorRule`: faz o `LiveData` funcionar no teste unitário | **Adicionada** na fase 2, nos dois módulos |
| `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")` | `runTest` e `Dispatchers.setMain`: faz o `viewModelScope.launch` rodar no teste | Ainda não precisou: os testes da fase 2 param antes das coroutines |
| `testImplementation("org.robolectric:robolectric")` | Simula o Android no computador | Não vai precisar: o `Patterns` foi trocado por `EmailUtils` |
| Firebase Emulator Suite (ferramenta `firebase-tools`, não é dependência Gradle) | Firestore e Auth locais, descartáveis, para os testes de integração | Fase 5 |

Os testes do Room (fase 4) **não** precisam de nada novo: `Room.inMemoryDatabaseBuilder` já vem no
`room-runtime`, e `androidx.test.ext:junit` já está no projeto.

## 3. Testes unitários

### Fase 1 — funções puras (feita)

Arquivos: `DocumentoUtilsTest`, `PedidoRepositorioTest`, `SaldoPedidoTest`, `PedidoComStatusTest`,
`FormatoM3Test` e `EventoTest` (os dois últimos nos dois módulos).

**`DocumentoUtils` (gerencial)** — `gerencial/src/test/.../dados/DocumentoUtilsTest.kt`

| Caso | Entrada | Esperado |
|---|---|---|
| CPF válido com máscara | `documentoValido("529.982.247-25")` | `true` |
| CPF com dígito errado | `documentoValido("529.982.247-24")` | `false` |
| CPF de números repetidos | `documentoValido("111.111.111-11")` | `false` |
| CNPJ válido | `documentoValido("11.222.333/0001-81")` | `true` |
| CNPJ com dígito errado | `documentoValido("11.222.333/0001-80")` | `false` |
| Tamanho que não é CPF nem CNPJ | `documentoValido("1234567890")` | `false` |
| Tipo de pessoa | `tipoPessoa` com 11 / 14 / 12 dígitos | `FISICA` / `JURIDICA` / `null` |
| Máscara parcial de CPF | `formatar("1234")` | `"123.4"` |
| Máscara completa de CPF | `formatar("52998224725")` | `"529.982.247-25"` |
| Máscara de CNPJ | `formatar("11222333000181")` | `"11.222.333/0001-81"` |
| Dígitos a mais são cortados | `formatar("112223330001819")` | `"11.222.333/0001-81"` |
| Limpeza | `apenasDigitos("529.982.247-25")` | `"52998224725"` |

**`PedidoRepositorio.calcularStatus` (gerencial)** — é pura: não toca o Firebase.

| Caso | m³ alocados / total / tem viagem pendente | Esperado |
|---|---|---|
| Nada alocado | 0 / 10 / não | `PENDENTE` |
| Parte alocada | 4 / 10 / sim | `EM_ANDAMENTO` |
| Tudo alocado, ainda entregando | 10 / 10 / sim | `EM_ANDAMENTO` |
| Tudo alocado e entregue | 10 / 10 / não | `CONCLUIDO` |
| **Arredondamento** | 0,7 + 0,1 + 0,1 + 0,1 / 1,0 / não | `CONCLUIDO` |
| **Arredondamento** | 0,1 + 0,2 − 0,1 − 0,2 (tudo cancelado) / 1,0 / não | `PENDENTE` |

> Os dois casos de arredondamento **falhavam** e revelaram um bug real: em `Double`, 0,7 + 0,1 +
> 0,1 + 0,1 dá 0,9999999999999999 e 9,1 − 8 dá 1,0999999999999996. Com isso o pedido nunca
> concluía, o botão "Atribuir viagem" continuava aparecendo e atribuir **exatamente** o saldo
> restante era recusado por "saldo insuficiente". Correção: a conta do saldo agora fica só em
> `SaldoPedido` (gerencial, pacote `dados`), que arredonda para o litro (3 casas), e a transação
> grava os m³ reservados já arredondados.

**`SaldoPedido` (gerencial)** — a conta do saldo usada pela lista, pelo histórico, pela tela de
atribuir e pela transação de atribuir/cancelar.

| Caso | Esperado |
|---|---|
| `calcular(9.1, 8.0)` | exatamente `1.1` (antes: 1,0999999999999996) |
| `calcular(7.3, 7.0)` | exatamente `0.3` |
| `calcular(1.0, 0.7 + 0.1 + 0.1 + 0.1)` | `0.0` |
| `calcular(0.3, 0.1 + 0.2)` | `0.0` (e não um negativo minúsculo) |
| `calcular(8.125, 0.0)` | `8.125` (casas de litro não são perdidas) |
| `arredondar(0.7 + 0.1 + 0.1 + 0.1)` | `1.0` |

**`formatarM3` e `formatarKm` (os dois módulos)** — fixar `Locale.setDefault(Locale.forLanguageTag("pt-BR"))` no `@Before`.

| Caso | Esperado |
|---|---|
| `formatarM3(20.0)` | `"20"` |
| `formatarM3(6.5)` | `"6,5"` |
| `formatarKm(12.0)` | `"12,0"` |
| `formatarKm(12.34)` | `"12,3"` |
| `formatarM3(6.25)` (duas casas) | decidir o esperado: hoje mostra uma casa só |

**Outros:**

- `Evento`: a 1ª chamada de `getConteudoSeNaoTratado()` devolve o valor; a 2ª devolve `null`.
- `PedidoComStatus.saldo`: 9,1 com 8 reservados → exatamente 1,1; 1,0 entregue em parcelas
  quebradas → 0.
- Ficaram de fora de propósito: `Pedido.enderecoResumido()`, `Caminhao.toString()` e
  `MotoristaComId.toString()`. Eles só montam um texto; um teste ali só quebraria quando alguém
  mudasse o formato de propósito, e nunca pegaria um bug.

### Fase 2 — validações dos ViewModels (feita)

Arquivos: `CadastroCaminhaoViewModelTest`, `CadastroMotoristaViewModelTest`,
`CadastroGerenteViewModelTest`, `NovoPedidoViewModelTest`, `AtribuirViagemViewModelTest`,
`LoginViewModelTest` (nos dois módulos) e `EmailUtilsTest`.

Todos esses testes param **antes** de chamar o repositório (a validação falha e a função retorna),
então não tocam o Firebase. Para isso, o formulário "padrão" de cada teste sempre tem um campo
errado de propósito (por exemplo, a data de entrega vazia no pedido). Confere-se o valor de
`viewModel.estado.value`.

Os testes dos cadastros de motorista e de gerente **falhavam**: o `android.util.Patterns` fica
vazio no computador. Foi o que levou a criar o `EmailUtils`. O `LoginViewModel` do motorista só
pôde ser testado depois de deixar de depender de `Context` (antes ele era um `AndroidViewModel`).

| ViewModel | Caso | Esperado no estado |
|---|---|---|
| `CadastroCaminhaoViewModel.criar` | placa `"AB1234"` | `erroCampoPlaca = true` |
| | placa `"abc1d23"` (Mercosul, minúscula) | sem erro de placa (vira maiúscula) |
| | ano `"1969"` / `"2101"` / `"abc"` | `erroCampoAno = true` |
| | marca vazia | `erroMarcaModelo = true` |
| `NovoPedidoViewModel.salvar` | CPF inválido | `erroDocumento = true` |
| | CEP com 7 dígitos, ou UF `"XX"` | `erroEndereco = true` |
| | quantidade `"0"`, `"-1"`, `"abc"` | `erroQuantidade = true` |
| | quantidade `"2,5"` | aceita (vírgula vira ponto) |
| | distância vazia | `erroDistanciaKm = true` |
| | sem data de entrega | `erroDataEntrega = true` |
| `AtribuirViagemViewModel.atribuir` | quantidade `""`, `"0"`, `"abc"` | `erroQuantidade = true` |
| `LoginViewModel.entrar` (os dois apps) | email ou senha vazios | `erro = CAMPOS_VAZIOS` |
| `CadastroMotoristaViewModel.criar` | CPF com 10 dígitos, ou dígito verificador errado | `erroCampoCpf = true` |
| | email `"ana@"` | `erroCampoEmail = true` |
| | senha com 5 caracteres / com 6 | `erroCampoSenha = true` / `false` |
| `CadastroGerenteViewModel.criar` | nome vazio, email sem @, senha curta | o erro do campo correspondente |
| `EmailUtils.valido` | `ana@usina.com.br`, `joao.silva+obra@concreto.io` | `true` |
| | `ana`, `ana@`, `@usina.com`, `ana@usina`, `ana silva@usina.com` | `false` |

### Fase 3 — regras que hoje estão "presas" (precisa extrair funções puras antes)

**Rateio de km** (hoje dentro de `RoteiroRepositorio.encerrarRoteiro`, motorista). Extrair algo
como `calcularRateio(kmTotal, viagens, distanciasPorPedido): Map<String, Double>`.

| Caso | Esperado |
|---|---|
| 2 viagens com o mesmo peso, 100 km | 50 km cada |
| Pesos 1 : 3, 100 km | 25 e 75 |
| 3 pesos iguais, 100 km | soma **exatamente** 100 (a última absorve o resto de 33,33…) |
| Uma viagem cancelada no meio | a cancelada não recebe km e não dilui as outras |
| Todas com peso zero (distância 0) | ninguém recebe km (hoje só gera um `Log.w`) |
| Ordem | a última por `dataAtribuicao` é a que absorve o resto, não a última da lista |

O gerencial recalcula o mesmo percentual em `DetalhesRoteiroViewModel`. Os dois cálculos precisam
bater — um teste com os mesmos dados nos dois lados evita que alguém mude um e esqueça o outro.

**Distância Haversine** (hoje `private` em `PontoGpsRepositorio`). Extrair uma função que recebe
a lista de `PontoGps`:

| Caso | Esperado |
|---|---|
| Nenhum ponto ou 1 ponto | 0 km |
| Dois pontos iguais | 0 km |
| 1 grau de latitude (0,0 → 1,0) | ≈ 111,19 km (tolerância de 0,1) |
| Três pontos em linha | soma dos dois trechos |

**Botão da tela de detalhes** (`DetalhesViagemViewModel.decidirAcao`, motorista):

| Viagem | Roteiro da viagem | Roteiro ativo do motorista | Botão |
|---|---|---|---|
| CONCLUIDA ou CANCELADA | qualquer | qualquer | nenhum |
| ATRIBUIDA, sem roteiro | — | nenhum ou PLANEJANDO | Adicionar ao roteiro |
| ATRIBUIDA, sem roteiro | — | EM_ROTA | nenhum |
| com roteiro | PLANEJANDO | — | Remover do roteiro |
| com roteiro | EM_ROTA | — | Marcar entregue |
| com roteiro | não encontrado | — | nenhum |

**Aba Roteiro** (`RoteiroViewModel`, motorista):

- `podeIniciar` só é verdadeiro com roteiro PLANEJANDO **e** ao menos uma viagem ATRIBUIDA.
- `podeEncerrar` só é verdadeiro com roteiro EM_ROTA **e** todas as viagens CONCLUIDA ou CANCELADA.
- `iniciarRoteiro` só manda para EM_ROTA as viagens ATRIBUIDA — uma viagem que o gerente
  cancelou enquanto o roteiro era planejado **não pode voltar** para EM_ROTA.

## 4. Testes de integração

### Fase 4 — Room (motorista, `app/src/androidTest`, banco em memória)

Banco criado com `Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()` antes de
cada teste e fechado depois — nada fica gravado no celular.

| DAO | Caso | Esperado |
|---|---|---|
| `PontoGpsDao` | inserir 3 pontos fora de ordem e `buscarPorRoteiro` | volta ordenado por `horario` |
| | pontos de dois roteiros | `buscarPorRoteiro` só traz os do roteiro pedido |
| | `buscarPendentes` depois de `marcarSincronizado` em um | o marcado sai da fila |
| `EntregaPendenteDao` | inserir a mesma viagem duas vezes | fica uma só, com o `dataFim` mais novo (REPLACE) |
| | `remover` | some de `buscarPendentes` |

### Fase 5 — Firestore com o emulador local do Firebase

O **Firebase Emulator Suite** roda um Firestore e um Auth "de mentira" no computador
(`firebase emulators:start --only firestore,auth`). O teste aponta o app para ele com
`Firebase.firestore.useEmulator("10.0.2.2", 8080)` antes de qualquer outra chamada — assim os
testes **nunca** mexem nos dados reais. Cada teste começa limpando o banco do emulador.

**Gerencial — as escritas que mexem no saldo (as mais importantes do sistema):**

| Função | Caso | Esperado |
|---|---|---|
| `ViagemRepositorio.atribuir` | pedido de 10 m³, atribuir 4 | viagem ATRIBUIDA criada, `m3Alocados = 4` |
| | atribuir 11 num pedido de 10 | `SaldoInsuficienteException`, nada gravado |
| | **duas atribuições de 6 ao mesmo tempo** num pedido de 10 | exatamente uma dá certo (é o motivo da transação) |
| | pedido de 9,1 com 8 reservados, atribuir 1,1 | aceita, e `m3Alocados` fica exatamente 9,1 |
| | pedido de 1,0, atribuir 0,7, 0,1, 0,1 e 0,1 | `m3Alocados` fica exatamente 1,0 |
| `ViagemRepositorio.cancelar` | cancelar viagem ATRIBUIDA de 4 | CANCELADA com motivo, `m3Alocados` volta 4 |
| | cancelar viagem CONCLUIDA | `EstadoInvalidoException` |
| | cancelar duas vezes | a 2ª dá `EstadoInvalidoException`; saldo devolvido só uma vez |
| | atribuir 0,1 e 0,2 e cancelar as duas | `m3Alocados` volta a exatamente 0 |
| `PedidoRepositorio.cadastrar` | dois cadastros seguidos | números sequenciais (4, 5…) |
| | dois cadastros ao mesmo tempo | números diferentes |
| `PedidoRepositorio.listarComStatus` | pedidos com datas diferentes | ordenados pela data de entrega |
| `CaminhaoRepositorio.cadastrar` | placa repetida | `PlacaJaCadastradaException`, o primeiro não é sobrescrito |

**Motorista — roteiro:**

| Função | Caso | Esperado |
|---|---|---|
| `RoteiroRepositorio.adicionarViagem` | motorista sem roteiro | cria roteiro PLANEJANDO e liga a viagem a ele |
| | 2ª viagem, mesmo caminhão | entra no mesmo roteiro |
| | 2ª viagem, outro caminhão | `CaminhaoIncompativelException` |
| | roteiro já EM_ROTA | `RoteiroJaIniciadoException` |
| `removerViagem` | remover a única viagem | o documento do roteiro é apagado |
| `iniciarRoteiro` | roteiro com 2 viagens | roteiro e as 2 viagens EM_ROTA, com `dataInicio` |
| `confirmarEntregaNoServidor` | viagem EM_ROTA | vira CONCLUIDA com `dataFim` |
| | **viagem que o gerente cancelou** | continua CANCELADA (é o motivo da transação) |
| `encerrarRoteiro` | 3 viagens concluídas, 100 km | soma dos `kmPercorridos` = 100; roteiro CONCLUIDO |
| `ViagemRepositorio.buscarAbertasDoMotorista` | viagens de 2 motoristas | só as do próprio, ATRIBUIDA e sem roteiro |
| `RoteiroRepositorio.marcarEntregue` | emulador desligado | devolve `false` e grava a entrega na fila do Room |

Atenção: consultas com `whereEqualTo` + `whereIn` passam no emulador mesmo sem índice, mas no
Firestore de verdade podem exigir um índice composto. Se um teste passar e o app real falhar com
"requires an index", é isso.

**Regras de segurança** (opcional, depois de exportar `firestore.rules`): motorista não consegue
alterar viagem de outro motorista; motorista não consegue gravar em `pedidos`; um gerente não
consegue escrever o perfil de outro gerente.

## 5. Fase 6 — testes manuais no celular (campo)

O `CLAUDE.md` já avisa: GPS e serviço em segundo plano não são confiáveis no emulador. Roteiro de
testes para fazer com 1 ou 2 motoristas:

**Permissões e serviço**
- [ ] Negar localização → aparece o aviso e o roteiro não inicia.
- [ ] Permitir localização mas negar "o tempo todo" → o roteiro inicia; o rastro só é gravado com o app aberto.
- [ ] Iniciar roteiro → notificação fixa aparece; encerrar → notificação some.
- [ ] Deixar o app em segundo plano 10 min, com a tela apagada → o trajeto continua sendo gravado.
- [ ] Com otimização de bateria ligada, aparece o botão de ajuste; depois de isentar, o botão some.
- [ ] Forçar a parada do app durante o roteiro e reabrir a aba Roteiro → o rastreamento volta sozinho.

**Sem sinal (fila offline)**
- [ ] Modo avião durante o roteiro por 5 min → ao voltar, os pontos sobem e o gerente vê o trajeto completo.
- [ ] Marcar entrega em modo avião → aviso de "enfileirada"; ao voltar o sinal, a viagem vira CONCLUIDA sozinha.
- [ ] **Gerente cancela a viagem enquanto o motorista está sem sinal; motorista marca entregue; volta o sinal** → a viagem continua CANCELADA e o saldo do pedido não muda.

**Fluxo completo entre os dois apps**
- [ ] Gerente cadastra pedido → atribui 2 viagens a um motorista → motorista vê as 2 na lista.
- [ ] Motorista monta o roteiro com as 2, inicia, gerente vê o caminhão se mexendo no mapa.
- [ ] Motorista entrega as 2 e encerra → gerente vê o km total e a tabela de rateio no detalhe do roteiro.
- [ ] Pedido com saldo zero e tudo entregue aparece como "concluído" na lista do gerente.

**Tela**
- [ ] Girar a tela no meio de cada formulário (novo pedido, cadastros, atribuir) → nada digitado se perde.
- [ ] Girar a tela logo depois de um sucesso → o aviso não aparece duas vezes (é o papel do `Evento`).

## 6. Ordem sugerida

1. **Fase 1** — funções puras. **Feita:** 41 testes, e o bug do arredondamento corrigido.
2. **Fase 2** — validações dos ViewModels. **Feita:** 44 testes (só precisou do `core-testing`).
3. **Fase 3** — extrair rateio, Haversine e as decisões de botão para funções puras, e testá-las.
4. **Fase 4** — Room em memória.
5. **Fase 5** — Firestore no emulador (a mais trabalhosa de montar, e a que protege o saldo).
6. **Fase 6** — campo, antes de cada entrega de versão.

## Exemplo de um teste unitário (fase 1)

```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentoUtilsTest {

    @Test
    fun cpfComDigitosCorretosEhValido() {
        assertTrue(DocumentoUtils.documentoValido("529.982.247-25"))
    }

    @Test
    fun cpfComUltimoDigitoErradoEhInvalido() {
        assertFalse(DocumentoUtils.documentoValido("529.982.247-24"))
    }

    @Test
    fun mascaraDeCnpjEhAplicada() {
        assertEquals("11.222.333/0001-81", DocumentoUtils.formatar("11222333000181"))
    }
}
```

Cada função marcada com `@Test` é um teste. O `assertTrue`/`assertEquals` é a "conferência": se o
valor não for o esperado, o teste fica vermelho e mostra a diferença.
