# Migração para MVVM — design

Data: 2026-09-19

## Objetivo

Introduzir uma camada ViewModel entre as Views (Activities/Fragments) e os
repositórios existentes, nos dois apps (`app` = motorista, `gerencial`).
Depois desta migração: (1) revisão de consistência arquitetural dos dois
apps, (2) comentar todas as classes e métodos em português (o que fazem e
por que existem).

## Padrão adotado

- **View** (Activity/Fragment): infla layout, observa `LiveData`, repassa
  ações do usuário ao ViewModel. Mantém tudo que só a View pode fazer:
  pedidos de permissão (`registerForActivityResult`), abrir Intents
  (Google Maps, ajuste de bateria), iniciar/parar `RastreamentoService`,
  diálogos e `Toast`.
- **ViewModel** (`androidx.lifecycle.ViewModel`, um por tela): guarda o
  estado em `LiveData`, chama os repositórios via `viewModelScope.launch`.
  Nunca importa `Activity`, `Fragment`, `View` ou `Context`.
- **Model**: repositórios `object` em `backend/` (Firestore) e `db/` (Room)
  — não mudam.

### Estado da tela

Uma `data class` de estado por tela (ex.: `MinhasViagensUiState`), exposta
como uma única `LiveData<EstadoDaTela>`. Evita combinações inconsistentes
de campos (esquecer de desligar "carregando" quando o erro chega).

### Eventos de uma vez só

Classe utilitária `Evento<T>` (uma por módulo, em `ui/viewmodel/Evento.kt`)
para toast de sucesso e navegação — evita reexecutar o efeito quando o
`LiveData` reemite após girar a tela.

### Sem DI framework

Repositórios continuam `object`; ViewModels os referenciam direto. Sem
`ViewModelProvider.Factory` customizada, sem Hilt/Koin.

## Estrutura de pacotes

Pacote novo `ui.viewmodel` em cada módulo, separado de `ui` (que passa a
conter só Activities, Fragments e Adapters). Isso facilita a revisão de
consistência depois (checar por import que ViewModel não referencia
Android UI, e que View não chama repositório direto).

## Dependências novas

`androidx.lifecycle:lifecycle-viewmodel-ktx` e
`androidx.lifecycle:lifecycle-livedata-ktx`, versão 2.9.4 (mesma do
`lifecycle-runtime-ktx` já usado), em `libs.versions.toml` e nos dois
`build.gradle.kts`.

## Escopo — uma ViewModel por tela

**App motorista (4 telas):**
- `LoginActivity` → `LoginViewModel`
- `MinhasViagensActivity` → `MinhasViagensViewModel`
- `IniciarTransitoActivity` → `IniciarTransitoViewModel`
- `ViagemEmAndamentoActivity` → `ViagemEmAndamentoViewModel`

**App gerencial (11 telas):**
- `LoginActivity` → `LoginViewModel`
- `CadastroActivity` → `CadastroViewModel`
- `PedidosFragment` → `PedidosViewModel`
- `MapaFragment` → `MapaViewModel` (listener do Firestore passa a viver
  na ViewModel — sobrevive a giro de tela sem reinscrever)
- `ConfiguracoesFragment` → `ConfiguracoesViewModel`
- `NovoPedidoActivity` → `NovoPedidoViewModel`
- `HistoricoPedidoActivity` → `HistoricoPedidoViewModel`
- `AtribuirViagemActivity` → `AtribuirViagemViewModel`
- `CadastroMotoristaActivity` → `CadastroMotoristaViewModel`
- `CadastroCaminhaoActivity` → `CadastroCaminhaoViewModel`
- `RotaViagemActivity` → `RotaViagemViewModel`

`MainActivity` (só hospeda as abas) fica sem ViewModel.
`RastreamentoService`, repositórios, DAOs e adapters de RecyclerView não
mudam.

## Fora de escopo (por decisão explícita)

- Testes unitários dos ViewModels — fica para etapa futura.
- StateFlow — usa LiveData.

## Rollout

Incremental, uma tela por vez, com checkpoint: implementa, roda
`gradlew.bat assembleDebug`, avisa para teste manual no celular antes de
seguir para a próxima tela.

Status: **concluído em 2026-09-19.** As 15 telas (4 do app motorista, 11
do gerencial) foram migradas e testadas manualmente no celular, na ordem:
`MinhasViagensActivity` → `IniciarTransitoActivity` →
`ViagemEmAndamentoActivity` → `LoginActivity` (motorista); depois
`LoginActivity` → `CadastroActivity` → `PedidosFragment` →
`MapaFragment` → `ConfiguracoesFragment` → `NovoPedidoActivity` →
`HistoricoPedidoActivity` → `AtribuirViagemActivity` →
`CadastroMotoristaActivity` → `CadastroCaminhaoActivity` →
`RotaViagemActivity` (gerencial, sem checkpoint individual nas últimas 7).

Próximos passos combinados: revisão de consistência arquitetural e
comentar todas as classes/métodos dos dois apps.
