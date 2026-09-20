# Fluxo de viagens do motorista, GPS multi-viagem e km percorrido — design

Data: 2026-09-20

## Objetivo

1. Corrigir um bug real: o `RastreamentoService` só rastreia uma viagem por
   vez (campo único `viagemId`); iniciar uma segunda viagem substitui a
   primeira, que para de gravar pontos de GPS mesmo continuando "em rota"
   no Firestore.
2. Redesenhar o fluxo de telas do app motorista: hoje há uma lista de
   viagens não iniciadas + uma tela de iniciar + uma tela separada de
   viagem em andamento. Vira uma lista única de "viagens em aberto" e uma
   tela de detalhe só, com mapa e um botão que muda de função conforme o
   status.
3. Calcular e exibir o km percorrido por viagem, e o total por pedido
   (somando as viagens), nos dois apps.

## Escopo

Só o app motorista tem mudança de arquitetura (Service, novas telas).
O app gerencial ganha exibição de dados novos (km, painel de detalhes na
tela de rota) em telas que já existem — sem tela nova lá.

## 1. Telas e navegação (app motorista)

**Consolidação**: `IniciarTransitoActivity` e `ViagemEmAndamentoActivity`
são substituídas por uma única `DetalhesViagemActivity`.

**`MinhasViagensActivity` → renomeada para `ViagensAbertasActivity`**
(Activity, ViewModel, layout `activity_viagens_abertas.xml`): lista as
viagens do motorista com status `ATRIBUIDA` ou `EM_ROTA` juntas (hoje são
tratadas em UIs separadas: lista + botão avulso). Cada item mostra
pedido, endereço, m³, caminhão e o status ("Atribuída" / "Em rota").
Tocar num item abre `DetalhesViagemActivity` com o id da viagem.

**`DetalhesViagemActivity`** (layout `activity_detalhes_viagem.xml`):
- Dados do pedido: destinatário, endereço, m³, caminhão (como
  `IniciarTransitoActivity` já mostra hoje).
- Um mapa (`SupportMapFragment`, como as demais telas de mapa do
  projeto):
  - **Viagem `ATRIBUIDA`** (não iniciada): ao abrir a tela, pede
    permissão de localização se ainda não concedida (decisão já
    tomada — diferente de hoje, que só pedia ao iniciar) e mostra um
    marcador com a posição atual — uma leitura pontual
    (`FusedLocationProviderClient.getCurrentLocation`), não liga o
    rastreamento em segundo plano. Sem pontos salvos ainda, então sem
    Polyline.
  - **Viagem `EM_ROTA`**: mapa mostra a Polyline do trajeto já
    percorrido (mesmo desenho de `RotaViagemActivity` no gerencial),
    mais o km percorrido até agora e a data/hora de início
    (formatada).
- Um botão que muda de função:
  - `ATRIBUIDA` → **"Iniciar"**. Toque abre um `AlertDialog`
    ("Iniciar o trânsito?"); confirmando, pede permissão de segundo
    plano se necessário, chama `RastreamentoService.iniciar(context,
    viagemId)`, atualiza o status no Firestore (`iniciarTransito`,
    como hoje) e recarrega a tela (agora em modo `EM_ROTA`).
  - `EM_ROTA` → **"Encerrar"**. Toque abre um `AlertDialog`
    ("Finalizar a viagem?"); confirmando, calcula o km percorrido a
    partir dos pontos locais (Room), grava `kmPercorridos`, `status` e
    `dataFim` no Firestore numa escrita só (igual ao `finalizarViagem`
    de hoje, sem transação — funciona offline), chama
    `RastreamentoService.encerrar(context, viagemId)`, mostra a
    data/hora de término e o km final, e volta pra lista de viagens
    em aberto.
- Continua mostrando o botão de ajuste de otimização de bateria
  enquanto alguma viagem estiver em rota (mesmo comportamento de hoje).
- Continua escutando cancelamento remoto (`ViagemRepositorio.
  escutarViagem`, hoje já existe em `ViagemEmAndamentoViewModel`)
  enquanto a viagem estiver `EM_ROTA`: se o gerente cancelar, chama
  `RastreamentoService.encerrar(context, viagemId)`, avisa o motorista
  e volta pra lista de viagens em aberto — mesmo comportamento de
  hoje, só que agora dentro da tela consolidada.

Arquivos removidos: `IniciarTransitoActivity.kt`,
`IniciarTransitoViewModel.kt`, `ViagemEmAndamentoActivity.kt`,
`ViagemEmAndamentoViewModel.kt`, `activity_iniciar_transito.xml`,
`activity_viagem_em_andamento.xml`.

## 2. `RastreamentoService` com múltiplas viagens

Troca o campo único `viagemId: String?` por um conjunto
`viagensAtivas: MutableSet<String>`.

- `companion object` ganha `iniciar(context, viagemId)` e
  `encerrar(context, viagemId)`, que montam um Intent com uma `action`
  (`ACTION_INICIAR` / `ACTION_ENCERRAR`) e o `EXTRA_VIAGEM_ID`, e chamam
  `startForegroundService`/`startService`.
- `onStartCommand` lê a `action`:
  - `ACTION_INICIAR`: adiciona o id ao conjunto. Só chama
    `requestLocationUpdates` se essa for a primeira viagem ativa (se já
    tiver outra rodando, o GPS já está ligado, só entra no conjunto).
  - `ACTION_ENCERRAR`: remove o id do conjunto. Se o conjunto ficar
    vazio, para as atualizações de localização e chama `stopSelf()`; se
    sobrar viagem ativa, o serviço continua rodando pra ela.
- `onLocationResult`: para cada id em `viagensAtivas` (cópia do
  conjunto), grava um `PontoGps` local, dispara a sincronização da fila
  offline (já é por ponto, não por viagem — não muda) e atualiza
  `ultimaLatitude`/`ultimaLongitude` no documento daquela viagem.
- Notificação fixa: texto genérico (não menciona uma viagem específica),
  já que pode haver mais de uma.

**Efeito colateral corrigido**: hoje, cancelamento remoto pelo gerente
(`tratarCancelamentoRemoto`) e finalizar viagem chamam
`stopService(...)`, que mata o serviço inteiro — se houvesse outra
viagem em rota, ela também pararia de ser rastreada. Os dois passam a
chamar `RastreamentoService.encerrar(context, viagemId)`.

## 3. Cálculo de km

- Fórmula de Haversine (distância entre coordenadas na superfície da
  Terra) somada entre pontos de GPS consecutivos de uma viagem.
- Novo método `PontoGpsRepositorio.calcularKmPercorridos(context,
  viagemId): Double` — busca os pontos locais (`PontoGpsDao.
  buscarPorViagem`, já existe) e soma as distâncias. Recalculado sob
  demanda (não é um contador incremental à parte); com um ponto a cada
  10s, uma viagem de horas tem no máximo algumas centenas de pontos —
  soma na hora sem custo perceptível.
- `ViagemRemota` (motorista e gerencial) ganha `val kmPercorridos:
  Double? = null`. Gravado uma vez, ao encerrar a viagem, junto com
  `status`/`dataFim` — não em tempo real durante a viagem (decisão já
  tomada: menos escritas, sem risco de conflito).
- **Total do pedido**: não é gravado — segue o mesmo padrão já usado
  pelo `status` do pedido ("não é gravado, é calculado"). O app
  gerencial soma `kmPercorridos` de todas as viagens do pedido na hora
  de exibir.

## 4. Onde o km aparece

- **`DetalhesViagemActivity`** (motorista): km percorrido até agora
  (viagem em rota, recalculado ao reabrir/atualizar a tela) e o total
  final depois de encerrar.
- **`RotaViagemActivity`** (gerencial): ganha um painel de detalhes no
  topo da tela (acima do mapa) com pedido nº + cliente, m³
  transportados, data/hora de início e término, e km da viagem. Hoje
  essa tela só tem o mapa. A `RotaViagemViewModel` passa a buscar
  também a viagem (`ViagemRepositorio.buscarPorId`, novo método — hoje
  só existe no motorista) e o pedido (`PedidoRepositorio.buscarPorId`,
  já existe).
- **`HistoricoPedidoActivity`** (gerencial): mostra o total de km do
  pedido (soma de todas as viagens), perto do progresso de m³; cada
  item da lista de viagens (`ViagemAdapter`) passa a mostrar também o
  km daquela viagem específica, junto com m³/datas que já aparecem.

## Fora de escopo

- Prevenir/impedir viagens simultâneas do mesmo motorista — decisão
  explícita: o app passa a suportar isso corretamente em vez de
  bloquear.
- Km em tempo real no pedido durante a viagem.
- Qualquer mudança no app gerencial além dos itens acima (sem tela
  nova lá).
