# Roteiro com múltiplos pedidos, rateio de km e redesenho do GPS — design

Data: 2026-09-20

## Objetivo

Hoje o motorista executa uma viagem (um pedido) de cada vez: inicia, dirige,
encerra. Na prática ele costuma sair da usina com várias entregas na
carroceria/carga e visitar vários clientes numa única saída de caminhão. Esta
mudança introduz o **Roteiro**: um agrupamento de viagens que o próprio
motorista monta antes de sair, inicia de uma vez, vai marcando como entregue
conforme visita cada cliente, e encerra ao voltar pra base — com o km
realmente percorrido (GPS) sendo **rateado entre os pedidos do roteiro** por
uma fórmula proporcional a volume × distância declarada.

Isso **substitui** o modelo implementado na branch anterior
(`2026-09-20-fluxo-viagens-motorista-e-km-design.md`), onde cada viagem tinha
seu próprio rastreamento GPS e seu próprio cálculo de km (Haversine sobre os
pontos daquela viagem). Nesta mudança, os pontos de GPS passam a pertencer ao
**roteiro**, não à viagem — km por viagem deixa de ser medido diretamente e
passa a ser um valor **calculado por rateio** a partir do km total do
roteiro.

## Decisões já tomadas (confirmadas com o usuário)

- Roteiro substitui o rastreamento por viagem — não convivem os dois modelos.
- Iniciar o roteiro muda o status de todas as viagens dele para `EM_ROTA` de
  uma vez. Marcar uma entrega como concluída muda só aquela viagem pra
  `CONCLUIDA`, individualmente, enquanto o roteiro e as demais continuam em
  rota.
- Encerrar o roteiro é um botão manual (sem checagem de GPS/geofencing).
- `distanciaDedicadaKm` do pedido é digitada manualmente pelo gerente (ida
  simples, base até o cliente) — sem integração de mapas.
- Viagem cancelada remotamente durante o roteiro sai do rateio (peso dela não
  entra no `pesoTotal`; as demais são recalculadas sobre o total sem ela).
- Um motorista só pode ter um roteiro (`PLANEJANDO` ou `EM_ROTA`) por vez.
- Encerrar o roteiro é bloqueado enquanto houver viagem sem resolver (nem
  `CONCLUIDA` nem `CANCELADA`) — evita ambiguidade no rateio.
- A aba "viagens" do motorista mostra só as que ainda não entraram em nenhum
  roteiro; ao entrar no roteiro, a viagem some dessa lista.
- No gerencial, clicar numa viagem abre o **roteiro completo** (mapa do
  trajeto inteiro + tabela de rateio de todos os pedidos), com a viagem
  clicada destacada — não mais uma tela por viagem.
- Todas as viagens de um roteiro precisam usar o mesmo caminhão (o roteiro
  não tem campo de caminhão próprio — é derivado/validado a partir das
  viagens que contém). Descoberto e corrigido na autorrevisão da spec: o
  primeiro rascunho tinha um campo `caminhaoPlaca` redundante no Roteiro,
  que conflitava com o caminhão já escolhido por viagem na atribuição.
- Só é possível adicionar viagem a um roteiro enquanto ele está
  `PLANEJANDO` — depois de `EM_ROTA`, nenhuma viagem nova entra nele
  (o motorista já saiu da base com o conjunto definido).
- Segue direto na branch/worktree atual (`worktree-fluxo-viagens-motorista-km`),
  sem esperar a branch anterior ser testada/mesclada — decisão explícita do
  usuário, ciente do risco de empilhar mudança não validada.

## 1. Modelo de dados

**`Pedido`** ganha:
```
distanciaDedicadaKm: Double   // km, ida simples, base até o destino — obrigatório, > 0
```

**Novo: `Roteiro`** (coleção `roteiros` no Firestore)
```
id: String                     // id do documento
motoristaUid: String
status: String                 // PLANEJANDO | EM_ROTA | CONCLUIDO
dataInicio: Long?               // gravado ao iniciar o roteiro
dataFim: Long?                  // gravado ao encerrar o roteiro
kmTotalRota: Double?             // gravado ao encerrar (Haversine sobre os pontos do roteiro)
```
Sem campo de caminhão próprio: cada `Viagem` já tem seu `caminhaoPlaca`,
escolhido pelo gerente na hora de atribuir (regra existente). Como um
roteiro representa **um** caminhão fazendo várias entregas, a regra de
consistência é validada na hora de adicionar: **uma viagem só pode entrar
num roteiro se usar o mesmo `caminhaoPlaca` das viagens já presentes nele**
(o roteiro vazio aceita qualquer caminhão, define o caminhão do roteiro
implicitamente pela primeira viagem adicionada). Tentar adicionar uma
viagem com caminhão diferente mostra um erro claro ("Esta viagem usa um
caminhão diferente do roteiro atual") e não deixa adicionar.

**`Viagem`** ganha:
```
roteiroId: String?             // null = ainda não foi lançada em nenhum roteiro
```
O `status` da viagem continua tendo os mesmos 4 valores de hoje
(`ATRIBUIDA`/`EM_ROTA`/`CONCLUIDA`/`CANCELADA`), só que agora `EM_ROTA` e
`CONCLUIDA` são setados em lote/individualmente pelo roteiro, não mais por
"iniciar transito"/"finalizar viagem" isolados.

**`PontoGps`**: troca `viagemId` por `roteiroId` — os pontos pertencem ao
roteiro. No Firestore, a subcoleção também muda de
`viagens/{viagemId}/pontosGps` para `roteiros/{roteiroId}/pontosGps`.

**Room**: como o esquema da tabela `pontos_gps` muda de coluna
(`viagemId` → `roteiroId`), a versão do banco sobe e a migração pode ser
destrutiva (`fallbackToDestructiveMigration`) — pontos de GPS são dados
efêmeros/já sincronizados com o Firestore quando confirmados, perder a fila
local pendente num upgrade de versão do app é aceitável pra esse projeto
(sem usuários em produção ainda).

## 2. O que é removido/substituído da branch anterior

- `RastreamentoService.viagensAtivas: MutableSet<String>` volta a ser um
  único alvo (`roteiroAtivo: String?`) — já que só existe um roteiro ativo
  por motorista por vez, a complexidade de conjunto não é mais necessária.
  `iniciar(context, roteiroId)`/`encerrar(context, roteiroId)` (mesma forma,
  parâmetro renomeado).
- `PontoGpsRepositorio.calcularKmPercorridos(context, viagemId)` vira
  `calcularKmPercorridos(context, roteiroId)` — mesma fórmula de Haversine,
  só muda o que está sendo somado (pontos do roteiro inteiro, não de uma
  viagem).
- `DetalhesViagemViewModel.confirmarEncerramento` (calcula km e chama
  `finalizarViagem` com o km calculado) deixa de existir nesse formato —
  "encerrar" vira uma ação em nível de roteiro (`encerrarRoteiro`), que
  calcula o km total UMA vez e faz o rateio; a viagem individual só é
  "marcada como entregue" (sem cálculo de km nesse momento).
- `DetalhesViagemViewModel.escutarCancelamento`'s cálculo de km ao cancelar
  desaparece — não tem mais como medir o km só daquela viagem (os pontos são
  do roteiro). Uma viagem cancelada simplesmente fica de fora do rateio
  (peso zero), sem precisar calcular nada no momento do cancelamento.
- `RotaViagemActivity` (gerencial) deixa de mostrar o trajeto de uma viagem
  isolada — vira `DetalhesRoteiroActivity`, mostrando o roteiro inteiro.
- O mapa dentro de `DetalhesViagemActivity` (motorista) deixa de fazer
  sentido por viagem (não tem mais "o trajeto desta viagem") — o mapa ao
  vivo do trajeto percorrido passa a viver na aba "Roteiro" (ver seção 4),
  mostrando o trajeto do roteiro inteiro; `DetalhesViagemActivity` continua
  mostrando os dados do pedido, mas sem mapa próprio.

## 3. Cálculo de km e rateio

Ao encerrar o roteiro:

1. `kmTotalRota = PontoGpsRepositorio.calcularKmPercorridos(context, roteiroId)`
   — Haversine sobre os pontos do roteiro (fórmula já existente, só muda o
   escopo).
2. Busca as viagens do roteiro com status `CONCLUIDA` (as `CANCELADA` ficam
   de fora do rateio — peso zero, `kmPercorridos` grava `0.0` ou fica `null`
   nelas).
3. Para cada viagem `i` concluída:
   `peso_i = volumeM3_i × distanciaDedicadaKm_i` (do pedido daquela viagem)
4. `pesoTotal = soma dos peso_i`
5. Para cada viagem `i`, exceto a última — a lista é ordenada por
   `dataAtribuicao` crescente (mesmo critério já usado em
   `buscarAbertasDoMotorista`), ordem estável e determinística:
   `kmAtribuido_i = kmTotalRota × (peso_i / pesoTotal)`
6. A **última** viagem da lista (maior `dataAtribuicao`) recebe o resto:
   `kmAtribuido_ultima = kmTotalRota - soma(kmAtribuido das anteriores)`
   — isso garante que a soma dos `kmAtribuido` seja **exatamente**
   `kmTotalRota`, sem sobra/falta por arredondamento de ponto flutuante.
7. Grava `kmPercorridos` em cada viagem `CONCLUIDA` do roteiro, e
   `status = CONCLUIDO`, `dataFim`, `kmTotalRota` no documento do roteiro —
   tudo isso numa única operação em lote (Firestore batch write), sem
   transação nem espera de confirmação de rede (mesmo raciocínio de sempre:
   funciona offline, sincroniza quando voltar o sinal).

Caso `pesoTotal == 0` (não deveria acontecer, já que "encerrar" exige pelo
menos uma viagem concluída, e toda viagem concluída tem `volumeM3 > 0`) —
tratado defensivamente: se ocorrer, nenhuma viagem recebe km (evita divisão
por zero), e o caso é logado.

## 4. Fluxo do motorista (telas)

**Nova Activity raiz com bottom navigation, duas abas:**

- **"Viagens"** (substitui a lista de hoje): mostra as viagens do motorista
  com `roteiroId == null` (ainda não lançadas em nenhum roteiro). Tocar
  numa abre `DetalhesViagemActivity`.
- **"Roteiro"**: mostra o roteiro atual do motorista (busca por
  `motoristaUid` + `status in [PLANEJANDO, EM_ROTA]`).
  - Vazio (nenhum roteiro): mensagem "nenhum roteiro em andamento" — o
    roteiro só é criado (lazy) quando a primeira viagem é adicionada.
  - `PLANEJANDO`: lista das viagens já adicionadas + botão "Iniciar
    roteiro" (habilitado só com 1+ viagem).
  - `EM_ROTA`: mapa com o trajeto percorrido até agora (Polyline sobre os
    pontos do roteiro, mesmo desenho de hoje só que em nível de roteiro) +
    lista das viagens com indicação de pendente/entregue + botão "Marcar
    como entregue" por item pendente + botão "Encerrar roteiro" (desabilitado
    enquanto houver viagem pendente, nem entregue nem cancelada).

**`DetalhesViagemActivity`** (tela de detalhe de uma viagem, sem mapa
próprio agora): mostra pedido/endereço/m³/caminhão como hoje, e o botão
muda conforme o contexto:
- `roteiroId == null` e o motorista **não tem roteiro `EM_ROTA`** (só pode
  ter, no máximo, um `PLANEJANDO`) → **"Adicionar ao roteiro"**: cria o
  roteiro (se ainda não tiver nenhum `PLANEJANDO`) ou usa o existente, valida
  o caminhão (seção 1), e seta `viagem.roteiroId`. O roteiro só aceita
  novas viagens enquanto está `PLANEJANDO` — uma vez iniciado (`EM_ROTA`),
  não é mais possível adicionar viagem nenhuma a ele (o motorista já saiu
  da base com as entregas definidas); nesse caso a viagem com
  `roteiroId == null` simplesmente não mostra nenhum botão de ação até o
  roteiro atual terminar.
- `roteiroId != null` e roteiro `PLANEJANDO` → **"Remover do roteiro"**:
  limpa `viagem.roteiroId`; se essa era a última viagem do roteiro, apaga o
  documento do roteiro (evita roteiro `PLANEJANDO` vazio órfão).
- `roteiroId != null` e roteiro `EM_ROTA` e viagem ainda não `CONCLUIDA` →
  **"Marcar como entregue"**: seta `viagem.status = CONCLUIDA`,
  `viagem.dataFim` (sem calcular km aqui — isso só acontece ao encerrar o
  roteiro inteiro). Mesmo botão que já existe na aba "Roteiro" — repetido
  aqui por conveniência, mesma ação.
- Viagem já `CONCLUIDA`/`CANCELADA` → sem botão de ação, só exibição.

Cada uma dessas ações (adicionar/remover/marcar entregue/iniciar
roteiro/encerrar roteiro) é uma escrita simples no Firestore, sem
transação, sem espera de confirmação — mesmo padrão já usado no projeto pra
escritas do motorista.

## 5. App gerencial

- `NovoPedidoActivity`: novo campo `distanciaDedicadaKm` (numérico, > 0,
  obrigatório).
- `RotaViagemActivity` → **`DetalhesRoteiroActivity`**: recebe o
  `roteiroId` (ou o `viagemId` clicado, buscando o roteiro dela). Mostra:
  - Mapa com o trajeto inteiro do roteiro (Polyline sobre
    `roteiros/{id}/pontosGps`).
  - Painel/tabela com cada pedido do roteiro: nº do pedido, cliente, m³,
    distância dedicada, peso teórico, % do rateio, km atribuído — a viagem
    que originou a navegação (se veio de um clique numa viagem específica)
    aparece destacada na lista.
  - Dados do roteiro: motorista, caminhão, início, fim, km total.
- Pontos de entrada pra essa tela: de dentro do histórico de um pedido
  (`HistoricoPedidoActivity`), clicar numa viagem que já tem `roteiroId`
  abre `DetalhesRoteiroActivity` com essa viagem destacada. Viagens sem
  `roteiroId` ainda (não lançadas em roteiro pelo motorista) não têm essa
  navegação disponível ainda.

## Fora de escopo

- Ordenação/otimização de rota (a ordem das paradas fica a critério do
  motorista, sem sugestão do sistema).
- Geofencing pra validar chegada na base ao encerrar o roteiro.
- Cálculo automático de `distanciaDedicadaKm` via mapa/API — continua
  manual.
- Qualquer mudança nas regras de saldo/m³ do pedido (atribuição continua
  igual, validada pelo gerente na hora de atribuir).
- Migração de dados existentes da branch anterior (pontos de GPS por
  viagem, viagens já com `kmPercorridos` calculado pelo modelo antigo) —
  como o projeto ainda não está em produção, não há dado real a migrar.
