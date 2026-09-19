# Projeto: entregas de concreto (usina com caminhões betoneira)

Sistema com dois apps Android e um backend, para acompanhar entregas de concreto em tempo real.
O usuário está aprendendo desenvolvimento Android (conteúdo de aula: Activity, Views, Layouts,
ciclo de vida, RecyclerView, Fragments, Services, SharedPreferences, SQLite). Explique em
português o que você escrever e por quê. Interface simples, sem enfeites.

## Apps

1. **App do motorista** (prioridade): vê as viagens atribuídas a ele, escolhe uma e inicia o
trânsito (rastreamento GPS), finalizando ao chegar no destino.
2. **App gerencial**: cadastra pedidos, motoristas e caminhões; atribui viagens (pedido + motorista
+ caminhão + quantidade); vê os caminhões em tempo real no mapa e o histórico de pedidos e viagens.
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

**Viagem** (um caminhão levando parte do pedido; um pedido tem várias viagens; criada pelo gerente
ao atribuir, não mais pelo motorista)

* pedido, motorista, caminhão
* m³ desta viagem (informado pelo gerente ao atribuir)
* status: atribuída, em rota, concluída, cancelada (com motivo)
* dataAtribuicao, início (quando o motorista inicia o trânsito), fim
* m³ efetivamente descarregado (opcional, padrão = m³ informado)

**Ponto GPS**: viagem, latitude, longitude, horário.

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
* Ao iniciar o trânsito (motorista): só muda o status de atribuída para em rota e liga o GPS; não
mexe no saldo, que já foi reservado na atribuição.
* Cancelar uma viagem (atribuída ou em rota) devolve os m³ ao saldo.
* A lista do motorista mostra as viagens atribuídas a ele que ainda não foram iniciadas (pedido,
endereço, m³, caminhão).
* Tipo de pessoa pode ser derivado do documento (11 dígitos = CPF, 14 = CNPJ); validar dígitos
verificadores e aplicar máscara.
* CEP pode preencher endereço via ViaCEP. UF em AutoCompleteTextView.
* O motorista vê nome, endereço e m³. CPF/CNPJ aparece só no cadastro e no app gerencial (LGPD).
* Rastrear a localização SOMENTE durante a viagem em rota.

## Fluxo do motorista

1. Login (email/senha cadastrados pelo gerente — o motorista não se cadastra).
2. Lista das viagens atribuídas a ele, ainda não iniciadas (pedido, endereço, m³, caminhão).
3. Escolhe uma viagem e toca em iniciar (muda o status para em rota, liga o serviço de GPS).
4. Navegação até o destino: abrir o Google Maps por Intent com o endereço (sem implementar rota).
5. No destino: finaliza a viagem, serviço de GPS parado.

## Fluxo do gerente

1. Login (email/senha).
2. Cadastra pedidos, motoristas (nome, CPF, email, senha) e caminhões (placa, marca, modelo, ano).
3. Atribui uma viagem: escolhe um pedido com saldo, um motorista, um caminhão e a quantidade em m³.
4. Acompanha os caminhões em rota no mapa em tempo real e o histórico de cada pedido.

## Requisitos técnicos

* Android Studio, telas em XML (Views), sem Jetpack Compose, para acompanhar o conteúdo das aulas.
* Lista de pedidos: RecyclerView.
* Persistência local: Room (SQLite). Configurações simples: SharedPreferences.
* GPS: serviço em primeiro plano (foreground service) com notificação fixa, tipo "location" no
manifest, permissões de localização (inclusive em segundo plano) e de notificações. Usar
FusedLocationProviderClient. Tratar isenção de otimização de bateria.
* Fila offline: pontos GPS ficam no banco local e sincronizam ao reconectar.
* Iniciar trânsito e finalizar viagem (motorista) não esperam confirmação do servidor — são
escritas simples (sem validar saldo), então a persistência offline do Firestore já basta: fica
guardada localmente e sincroniza sozinha quando a conexão voltar. Isso é diferente de atribuir
viagem (gerente) e cadastrar pedido, que usam transação e por isso *precisam* de conexão na hora
(dá erro claro se não tiver).
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

