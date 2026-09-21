# Projeto: entregas de concreto (usina com caminhões betoneira)

Sistema com dois apps Android e um backend, para acompanhar entregas de concreto em tempo real.
O usuário está aprendendo desenvolvimento Android (conteúdo de aula: Activity, Views, Layouts,
ciclo de vida, RecyclerView, Fragments, Services, SharedPreferences, SQLite). Explique em
português o que você escrever e por quê. Interface simples, sem enfeites.

## Apps

1. **App do motorista** (prioridade): vê as viagens atribuídas a ele, escolhe uma e inicia o
trânsito (rastreamento GPS), finalizando ao chegar no destino.
2. **App gerencial**: cadastra pedidos, motoristas e caminhões; atribui viagens (pedido + motorista
* caminhão + quantidade); vê os caminhões em tempo real no mapa e o histórico de pedidos e viagens.
Pode virar painel web depois (decisão em aberto).

O gerente decide quem leva o quê — o motorista só executa. Isso mudou no meio do projeto (as
etapas 1-4 foram implementadas com o motorista escolhendo pedido/caminhão/quantidade e iniciando
a viagem direto; a etapa 5 migrou isso para o gerente atribuir e o motorista só iniciar o trânsito).

## Modelo de dados

**Pedido** (cadastrado manualmente pelo gerente)

* número (id sequencial)
* destinatário: nome, CPF/CNPJ, tipo de pessoa (Física ou Jurídica), endereço
(rua, número, bairro, cidade, UF, CEP)
* quantidade total em m³ (decimal, maior que zero)
* distância dedicada em km (ida simples; usada só pra ratear o km percorrido entre pedidos de um
mesmo roteiro — ver "Rateio de km" em Regras de negócio)

**Viagem** (um caminhão levando parte do pedido; um pedido tem várias viagens; criada pelo gerente
ao atribuir, não pelo motorista)

* pedido, motorista, caminhão (placa denormalizada em `caminhaoPlaca`, pra exibir sem outra consulta)
* m³ desta viagem (informado pelo gerente ao atribuir)
* status: atribuída, em rota, concluída, cancelada (com motivo)
* dataAtribuicao, início (quando o *roteiro* que a contém é iniciado), fim (quando o motorista
marca essa viagem como entregue)
* m³ efetivamente descarregado (opcional, padrão = m³ informado)
* roteiro ao qual pertence (nulo enquanto não agrupada a um — ver "Roteiro")
* km percorridos (opcional; só é preenchido quando o roteiro é encerrado, pelo rateio)

**Roteiro** (um caminhão levando várias viagens de uma vez; agrupado e conduzido pelo motorista)

* motorista, caminhão (placa denormalizada)
* status: planejando (motorista ainda adicionando/removendo viagens), em rota, concluído
* dataInicio (quando o motorista inicia o trânsito), dataFim (quando encerra o roteiro)
* km total da rota (calculado do rastro de GPS ao encerrar, depois rateado entre as viagens — ver
"Rateio de km")
* Um motorista só pode ter um roteiro ativo (planejando ou em rota) por vez. Todas as viagens de
um mesmo roteiro usam o mesmo caminhão.

**Ponto GPS**: roteiro (não viagem — várias viagens em rota ao mesmo tempo, do mesmo roteiro,
compartilham o mesmo rastro), latitude, longitude, horário.

**Caminhão** (cadastrado pelo gerente): Placa (id), Marca, Modelo, Ano.

**Motorista** (cadastrado pelo gerente, inclusive email/senha de acesso): Nome, CPF, email.

## Regras de negócio

* O status do pedido NÃO é gravado, é calculado a partir das viagens:

  * m³ alocados = soma das viagens atribuídas + em rota + concluídas (canceladas não contam)
  * saldo = total do pedido - m³ alocados
  * pendente (nada alocado), em andamento (há saldo ou viagens atribuídas/em rota), concluído
(saldo zero e nenhuma viagem atribuída ou em rota)
* Ao atribuir viagem (gerente): m³ informado deve ser > 0 e <= saldo, e o saldo já é reservado
nesse momento (não quando o motorista inicia). Essa validação precisa ser atômica no servidor
(transação), porque o gerente pode atribuir mais de uma viagem ao mesmo pedido em sequência rápida.
* Ao iniciar o trânsito (motorista): inicia o *roteiro* inteiro — todas as suas viagens mudam de
atribuída para em rota de uma vez (escrita em lote) e o GPS liga; não mexe no saldo, que já foi
reservado na atribuição de cada viagem.
* Um roteiro só aceita viagem nova (adicionar/remover) enquanto está planejando, antes de ser
iniciado; a partir daí as viagens dele ficam fixas até serem entregues, canceladas ou o roteiro
ser encerrado.
* Rateio de km: ao encerrar o roteiro, o km total percorrido (calculado a partir do rastro de GPS)
é dividido entre as viagens concluídas do roteiro, proporcional ao peso de cada uma
(`peso = m³ da viagem × distância dedicada do pedido`). A última viagem (ordenada por
`dataAtribuicao`) absorve o resto da divisão de ponto flutuante, garantindo que a soma dos km
atribuídos bata exatamente com o km total. Viagens canceladas ficam de fora do peso total — não
recebem km nem "diluem" o rateio das outras.
* Cancelar uma viagem (atribuída ou em rota) devolve os m³ ao saldo.
* A lista do motorista mostra as viagens abertas atribuídas a ele — atribuídas OU em rota, juntas
numa lista só (pedido, endereço, m³, caminhão) — porque o motorista pode ter viagens em rota
(dentro de um roteiro ativo) ao lado de outras ainda não agrupadas a um roteiro.
* Tipo de pessoa pode ser derivado do documento (11 dígitos = CPF, 14 = CNPJ); validar dígitos
verificadores e aplicar máscara.
* CEP pode preencher endereço via ViaCEP. UF em AutoCompleteTextView.
* O motorista vê nome, endereço e m³. CPF/CNPJ aparece só no cadastro e no app gerencial (LGPD).
* Rastrear a localização SOMENTE durante o roteiro em rota (não por viagem individual — um roteiro
em rota pode ter várias viagens em rota ao mesmo tempo, todas compartilhando o mesmo rastro).

## Fluxo do motorista

1. Login (email/senha cadastrados pelo gerente — o motorista não se cadastra).
2. Lista de viagens abertas: as atribuídas a ele que ainda não foram concluídas (atribuídas + em
rota, juntas — pedido, endereço, m³, caminhão).
3. Escolhe uma ou mais viagens do mesmo caminhão e adiciona a um roteiro (cria um roteiro novo
"planejando" se ainda não tiver um ativo, ou junta na viagem existente).
4. Inicia o roteiro: todas as suas viagens viram "em rota" de uma vez, liga o serviço de GPS.
5. Navegação até o destino de cada viagem: abrir o Google Maps por Intent com o endereço (sem
implementar rota).
6. Ao entregar cada viagem, marca "entregue" individualmente (o roteiro continua em rota até
todas as suas viagens serem resolvidas — entregues ou canceladas).
7. Ao terminar todas as viagens do roteiro, encerra o roteiro: serviço de GPS parado, km total da
rota calculado e rateado entre as viagens concluídas.

## Fluxo do gerente

1. Login (email/senha).
2. Cadastra pedidos, motoristas (nome, CPF, email, senha) e caminhões (placa, marca, modelo, ano).
3. Atribui uma viagem: escolhe um pedido com saldo, um motorista, um caminhão e a quantidade em m³.
4. Acompanha os caminhões em rota no mapa em tempo real (por roteiro, não por viagem — um caminhão
= um roteiro) e o histórico de cada pedido, incluindo, no detalhe de um roteiro, a tabela de
rateio de km entre os pedidos que ele carregou.

## Requisitos técnicos

* Android Studio, telas em XML (Views), sem Jetpack Compose, para acompanhar o conteúdo das aulas.
* Lista de pedidos: RecyclerView.
* Persistência local: Room (SQLite). Configurações simples: SharedPreferences.
* GPS: serviço em primeiro plano (foreground service) com notificação fixa, tipo "location" no
manifest, permissões de localização (inclusive em segundo plano) e de notificações. Usar
FusedLocationProviderClient. Tratar isenção de otimização de bateria.
* Fila offline: pontos GPS ficam no banco local e sincronizam ao reconectar.
* Iniciar o roteiro (motorista) não espera confirmação do servidor — é uma escrita em lote simples
(sem validar saldo), então a persistência offline do Firestore já basta: fica guardada localmente
e sincroniza sozinha quando a conexão voltar.
* Marcar uma viagem como entregue é diferente: exige uma transação (lê o status atual da viagem
antes de gravar), porque uma confirmação de entrega enfileirada offline poderia, ao sincronizar,
sobrescrever um cancelamento que o gerente fez remotamente nesse meio-tempo — corrompendo o saldo
do pedido de volta. Pra não perder a resiliência offline que o motorista precisa em campo, quando
a transação não consegue completar na hora (sem sinal), a confirmação fica numa fila local (Room)
e é reenviada automaticamente a cada atualização de GPS do roteiro em rota, até confirmar — sem o
motorista precisar fazer nada.
* Isso é diferente de atribuir viagem (gerente) e cadastrar pedido, que também usam transação mas
*precisam* de conexão na hora (dá erro claro se não tiver) — não têm fila de reenvio, porque são
ações do gerente, feitas do escritório, não em campo sem sinal.
* minSdk sugerido: 26 (Android 8.0).
* Testar em celular real (GPS e serviço em segundo plano não são confiáveis no emulador).

## Etapas (fazer UMA por vez, na ordem)

1. Esqueleto do app do motorista com dados locais: login, lista de pedidos (RecyclerView com
dados fixos), tela de iniciar viagem com validação de saldo, Room.
2. Serviço de rastreamento em primeiro plano gravando pontos GPS no banco local.
3. Backend e sincronização (Firebase ou servidor próprio, decidir aqui), com fila offline e
validação de saldo em transação.
4. App gerencial: cadastro de pedidos, lista com progresso, mapa em tempo real, histórico de
pedidos e viagens.
5. Atribuição de viagens pelo gerente: cadastro de motoristas e de caminhões no app gerencial,
tela de atribuir viagem (pedido + motorista + caminhão + quantidade, com a validação de saldo
migrando pra esse momento), e o app do motorista passa a só listar e iniciar viagens atribuídas
(sem cadastro próprio nem escolha de caminhão/quantidade).
6. Robustez: cancelamento de viagem, falta de sinal, bateria, teste em campo com 1 ou 2 motoristas.

Depois da etapa 6, o app do motorista ganhou o conceito de **Roteiro** (agrupar várias viagens do
mesmo caminhão numa única saída, com o km percorrido calculado do GPS e rateado entre os pedidos
ao encerrar) — não estava no plano original de etapas, foi decidido à parte no meio do projeto.
Ver "Modelo de dados" e "Regras de negócio" pro detalhe.

## Como trabalhar comigo

* Implemente somente a etapa que eu pedir. Não avance sem eu autorizar.
* Antes de entregar, rode o build (`gradlew.bat assembleDebug` no Windows) e corrija os erros.
* Não faça commit sem eu pedir. Não adicione dependências sem me avisar e explicar.
* Comentários e mensagens de commit em português.
* Ao terminar cada etapa, diga o que testar manualmente no celular.

## Decisões em aberto

* Linguagem: Kotlin (padrão, alinhada às aulas).
* Backend: Firebase (Auth + Firestore). Sem Storage — não há fotos no projeto.
* App gerencial: app Android, módulo `:gerencial` no mesmo projeto Gradle.

## Padrões da Disciplina



Tema	O que os slides ensinam	Como conferir no projeto

Arquitetura	Activity/Fragment → ViewModel (LiveData) → Repository → Room/Retrofit	Activities e Fragments não chamam Room ou Firestore direto. ViewModel não importa Context, Activity nem View

Recursos	Textos em strings.xml via @string/..., temas em values, imagens em drawable/mipmap	Nenhum android:text com texto fixo nos layouts

Manifest	Declara Activities, Services e permissões	AndroidManifest.xml tem todas as telas, o RastreamentoService e as permissões de câmera e localização

Layouts e IDs	XML com padding, margin, gravity, weight. Os exemplos nomeiam IDs pelo tipo: textNome, buttonEnviar, checkReceberEmail	IDs seguem esse padrão e os layouts têm um arquivo por tela

Navegação	Intent para abrir outra tela, parâmetros por extras, finish() para fechar	Sem navegação feita de outro jeito

Ciclo de vida	Recursos criados em onCreate são liberados em onDestroy. onResume e onPause devem ser leves. Estado salvo em Bundle (onSaveInstanceState)	Girar a tela não perde os dados digitados. Nada pesado nos métodos de pausa

Log e diálogos	Log.d/i/w/e com categoria (tag), AlertDialog.Builder para confirmações	Sem println nem printStackTrace. Cancelar viagem usa AlertDialog

Fragments	Módulo reutilizável. onCreateView cria a view e onDestroyView libera recursos	Cada fragment funciona independente da Activity que o abriu

Listas	RecyclerView com LayoutManager, Adapter e ViewHolder, com o item em layout próprio	Não há ListView nem BaseAdapter

Service	Deve ser parado quando não é mais necessário (stopService/stopSelf) e usar startForeground() para rastrear	Existe um caminho que para o serviço ao finalizar ou cancelar a viagem, com notificação fixa

SharedPreferences	Só tipos primitivos, chaves como constantes, para configurações simples	Guarda login e viagem atual. Pedidos e viagens ficam no banco

SQLite	O acesso ao banco deve ficar fora da thread principal	Room sem allowMainThreadQueries()



