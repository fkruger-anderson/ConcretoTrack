# Roteiro com múltiplos pedidos, rateio de km e redesenho do GPS — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o rastreamento/cálculo de km por viagem individual por um modelo de Roteiro: o motorista monta o roteiro com várias viagens antes de sair, inicia/marca entregas/encerra em nível de roteiro, e o km real (GPS) é rateado entre os pedidos por peso (volume × distância dedicada).

**Architecture:** MVVM em toda parte (Activity/Fragment → ViewModel com LiveData → Repository `object` → Room/Firestore), seguindo os padrões já estabelecidos no projeto — inclusive o padrão de bottom navigation + Fragments já usado no `gerencial` (`MainActivity`/`PedidosFragment`/`MapaFragment`/`ConfiguracoesFragment`), agora replicado no `app` (motorista). O `RastreamentoService` passa a rastrear um único roteiro ativo por vez (mais simples que o modelo anterior de múltiplas viagens simultâneas, já que só pode haver um roteiro por motorista). Km é calculado uma vez por roteiro (Haversine sobre os pontos do Room) e rateado entre as viagens concluídas por peso (volume × distância dedicada), nunca gravado como total agregado — só o `kmTotalRota` no roteiro e o `kmAtribuido` (via `kmPercorridos`) em cada viagem.

**Tech Stack:** Kotlin, Android Views (XML), Room, Firebase Auth/Firestore, Google Maps SDK, FusedLocationProviderClient, `androidx.fragment:fragment-ktx` (já usado no `gerencial`, novo no `app`).

**Spec:** `docs/superpowers/specs/2026-09-20-roteiro-multiplos-pedidos-design.md`

## Global Constraints

- Comentários e mensagens de commit em português (CLAUDE.md).
- Sem framework de testes automatizado — cada task termina com `gradlew.bat assembleDebug` (build limpo) em vez de testes unitários; verificação manual no celular físico descrita ao final de cada task.
- Não commitar sem o usuário pedir — confirmar antes do primeiro commit real, conforme o resto da sessão.
- **Task 2 adiciona uma dependência nova ao módulo `app`**: `androidx.fragment:fragment-ktx` (alias `libs.androidx.fragment.ktx`, já existe no catálogo de versões e já é usada pelo `gerencial` — não é uma biblioteca nova ao projeto, só uma extensão a um segundo módulo). Avisar o usuário explicitamente antes de aplicar essa task (regra do CLAUDE.md: não adicionar dependência sem avisar/explicar).
- Nomes de arquivo/classe seguem o padrão já usado no projeto (Activity/Fragment/ViewModel/layout com o mesmo nome-base; Fragments seguem exatamente o padrão de `PedidosFragment`/`MapaFragment`/`ConfiguracoesFragment` do `gerencial`).
- **Classificação das escritas novas** (mesma lógica do resto do projeto: escritas que só fazem "escrever" sem precisar ler nada primeiro continuam sem esperar confirmação do servidor; escritas que precisam ler algo antes de decidir passam a exigir conexão, igual a "atribuir viagem"):
  - `RoteiroRepositorio.iniciarRoteiro` (motorista) e `RoteiroRepositorio.marcarEntregue` (motorista): escrita direta, sem transação, sem espera de confirmação — mesmo raciocínio de sempre, funciona offline.
  - `RoteiroRepositorio.adicionarViagem`, `removerViagem` e `encerrarRoteiro` (motorista): precisam ler dados existentes antes de decidir (validar caminhão consistente, achar/criar o roteiro ativo, buscar a distância dedicada de cada pedido para o rateio) — são `suspend fun` que esperam confirmação de rede, igual a `atribuir viagem`/`cadastrar pedido` no gerencial. Mostrar erro claro se não houver conexão (não trava a UI indefinidamente).
- Nenhuma escrita Firestore hoje fire-and-forget (iniciar trânsito, finalizar viagem — agora "iniciar roteiro"/"marcar entregue") ganha transação ou espera de rede nova.
- Possível necessidade de índice composto no Firestore: `RoteiroRepositorio.buscarRoteiroAtivo` (motoristaUid + status `whereIn`) segue o mesmo padrão de `ViagemRepositorio.buscarAbertasDoMotorista`, que já funciona sem índice composto configurado manualmente neste projeto — se o Firestore reclamar de índice ausente ao testar, o próprio erro no Logcat traz o link pra criar o índice automaticamente (avisar o usuário se isso acontecer).
- **Ordem das tasks é um grafo acíclico** (diferente do plano anterior, que teve um "compile unit" de 3 tasks circulares): cada task deste plano compila e builda sozinha, na ordem 1→2→3→4→5→6 (motorista) e 1→7, 1→8→9, 8→10 (gerencial, pode intercalar com o lado motorista já que são módulos Gradle separados). Não é necessário nenhuma ruling de "build só depois de outra task".

---

## Task 1: Modelo de dados — `distanciaDedicadaKm`, `roteiroId`, `RoteiroRemota`, `StatusRoteiro` (ambos os módulos)

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/Pedido.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/PedidoRemoto.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/PedidoRepositorio.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRemota.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/RoteiroRemota.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/StatusRoteiro.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/PedidoRemoto.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/PedidoRepositorio.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRemota.kt`
- Create: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/RoteiroRemota.kt`
- Create: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/dados/StatusRoteiro.kt`

**Interfaces:**
- Produces: `Pedido.distanciaDedicadaKm: Double` e `PedidoRemoto.distanciaDedicadaKm: Double` (ambos os módulos); `ViagemRemota.roteiroId: String?` (ambos); `RoteiroRemota` data class (ambos, com `motoristaUid`, `motoristaNome`, `status`, `dataInicio`, `dataFim`, `kmTotalRota`, `ultimaLatitude`, `ultimaLongitude`, `ultimoHorario`); `StatusRoteiro` enum (ambos: `PLANEJANDO`, `EM_ROTA`, `CONCLUIDO`).

- [ ] **Step 1: `StatusRoteiro` (motorista)**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/StatusRoteiro.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.db

/** Situação de um roteiro, espelhando o campo `status` do documento em `roteiros/{id}` no Firestore. */
enum class StatusRoteiro {
    /** O motorista está montando o roteiro (adicionando/removendo viagens), ainda não saiu. */
    PLANEJANDO,
    /** O motorista iniciou o roteiro; GPS ativo, viagens podem ser marcadas como entregues. */
    EM_ROTA,
    /** Todas as viagens do roteiro foram resolvidas (entregues ou canceladas) e o motorista encerrou. */
    CONCLUIDO
}
```

- [ ] **Step 2: `RoteiroRemota` (motorista)**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/RoteiroRemota.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.backend

/**
 * Documento em `roteiros/{id}` no Firestore. `motoristaNome` é denormalizado (copiado da viagem
 * que criou o roteiro) pelo mesmo motivo que `ViagemRemota` guarda `motoristaNome` além do uid:
 * evita uma consulta extra só pra exibir o nome. Sem campo de caminhão próprio — todas as viagens
 * de um roteiro usam o mesmo caminhão, validado na hora de adicionar (ver `RoteiroRepositorio`).
 */
data class RoteiroRemota(
    val motoristaUid: String = "",
    val motoristaNome: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null
)
```

- [ ] **Step 3: `Pedido` e `PedidoRemoto` ganham `distanciaDedicadaKm` (motorista)**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/Pedido.kt`, o campo final da data class vira:
```kotlin
    val quantidadeTotalM3: Double,
    val distanciaDedicadaKm: Double
) {
```
(Mantém o corpo da classe — `enderecoResumido()` — sem mudanças, só o parâmetro novo antes da chave.)

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/PedidoRemoto.kt`, o campo final vira:
```kotlin
    val quantidadeTotalM3: Double = 0.0,
    val m3Alocados: Double = 0.0,
    val distanciaDedicadaKm: Double = 0.0
)
```

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/PedidoRepositorio.kt`, dentro de `buscarPorId`, o `Pedido(...)` construído ganha o campo:
```kotlin
        return Pedido(
            id = id,
            destinatarioNome = remoto.destinatarioNome,
            rua = remoto.rua,
            numero = remoto.numero,
            bairro = remoto.bairro,
            cidade = remoto.cidade,
            uf = remoto.uf,
            cep = remoto.cep,
            quantidadeTotalM3 = remoto.quantidadeTotalM3,
            distanciaDedicadaKm = remoto.distanciaDedicadaKm
        )
```

- [ ] **Step 4: `ViagemRemota` ganha `roteiroId` (motorista)**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRemota.kt`, o campo final vira:
```kotlin
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null,
    val kmPercorridos: Double? = null,
    val roteiroId: String? = null
)
```

- [ ] **Step 5: `StatusRoteiro` (gerencial)**

Criar `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/dados/StatusRoteiro.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.dados

/** Espelha o enum do app motorista — o valor é gravado como texto no Firestore. */
enum class StatusRoteiro {
    PLANEJANDO,
    EM_ROTA,
    CONCLUIDO
}
```

- [ ] **Step 6: `RoteiroRemota` (gerencial)**

Criar `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/RoteiroRemota.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/** Espelha o documento em `roteiros/{id}` no Firestore (ver o app motorista para o raciocínio dos campos). */
data class RoteiroRemota(
    val motoristaUid: String = "",
    val motoristaNome: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null
)
```

- [ ] **Step 7: `PedidoRemoto` ganha `distanciaDedicadaKm` (gerencial)**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/PedidoRemoto.kt`, o campo final vira:
```kotlin
    val quantidadeTotalM3: Double = 0.0,
    val m3Alocados: Double = 0.0,
    val dataEntrega: Long = 0,
    val distanciaDedicadaKm: Double = 0.0
)
```

- [ ] **Step 8: `PedidoRepositorio.cadastrar` ganha o parâmetro (gerencial)**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/PedidoRepositorio.kt`, a assinatura de `cadastrar` e o `PedidoRemoto(...)` construído dentro dela ficam:
```kotlin
    suspend fun cadastrar(
        destinatarioNome: String,
        documento: String,
        tipoPessoa: String,
        rua: String,
        numero: String,
        bairro: String,
        cidade: String,
        uf: String,
        cep: String,
        quantidadeTotalM3: Double,
        dataEntrega: Long,
        distanciaDedicadaKm: Double
    ): Int {
        val contadorRef = db.collection("contadores").document("pedidos")

        val novoId = db.runTransaction { transacao ->
            val snapshot = transacao.get(contadorRef)
            val atual = snapshot.getLong("proximo") ?: 3L
            val proximo = atual + 1

            transacao.set(contadorRef, mapOf("proximo" to proximo))
            transacao.set(
                db.collection("pedidos").document(proximo.toString()),
                PedidoRemoto(
                    destinatarioNome = destinatarioNome,
                    documento = documento,
                    tipoPessoa = tipoPessoa,
                    rua = rua,
                    numero = numero,
                    bairro = bairro,
                    cidade = cidade,
                    uf = uf,
                    cep = cep,
                    quantidadeTotalM3 = quantidadeTotalM3,
                    m3Alocados = 0.0,
                    dataEntrega = dataEntrega,
                    distanciaDedicadaKm = distanciaDedicadaKm
                )
            )
            proximo
        }.aguardar()

        return novoId.toInt()
    }
```
(Só a assinatura e o `PedidoRemoto(...)` mudam — o resto do corpo do método permanece igual.)

Nota: `NovoPedidoActivity`/`NovoPedidoViewModel` ainda não passam esse novo parâmetro — isso quebraria a compilação. A Task 7 atualiza os dois. Até lá, o build desta task específica falha se rodado isoladamente nesse único ponto — **rode o Step 9 (build) só depois de também aplicar a Task 7**, ou, se quiser testar a Task 1 isolada agora, comente/ignore esse erro específico sabendo que é esperado (mencionar isso no relatório do implementador).

- [ ] **Step 9: `ViagemRemota` ganha `roteiroId` (gerencial)**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRemota.kt`, o campo final vira:
```kotlin
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val kmPercorridos: Double? = null,
    val roteiroId: String? = null
)
```

- [ ] **Step 10: Build**

Run: `gradlew.bat assembleDebug`
Expected: falha em UM ponto só — `NovoPedidoViewModel.salvar` chamando `PedidoRepositorio.cadastrar` sem o parâmetro `distanciaDedicadaKm` novo (gerencial). Esse é o único erro esperado nesta task (Step 8 já avisa disso) — a Task 7 corrige. Se aparecer qualquer OUTRO erro de compilação, é um problema real desta task, não o esperado — investigar antes de prosseguir.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/Pedido.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/PedidoRemoto.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/PedidoRepositorio.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRemota.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/RoteiroRemota.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/StatusRoteiro.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/PedidoRemoto.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/PedidoRepositorio.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRemota.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/RoteiroRemota.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/dados/StatusRoteiro.kt
git commit -m "Adiciona distanciaDedicadaKm, roteiroId e o modelo de Roteiro (ambos os apps)"
```

---

## Task 2: `MotoristaHomeActivity` (bottom navigation) + `ViagensFragment` + `RoteiroFragment` (placeholder)

**Contexto:** reestrutura a tela inicial do motorista de uma Activity única para um host com bottom navigation + 2 Fragments, seguindo exatamente o padrão já usado no `gerencial` (`MainActivity`/`PedidosFragment`/`MapaFragment`/`ConfiguracoesFragment`). `ViagensFragment` reaproveita `ViagensAbertasViewModel`/`ViagemAbertaAdapter`/`ViagemAbertaComPedido` sem nenhuma mudança nelas (essas classes já filtram certo depois da Task 3 — por enquanto continuam mostrando as mesmas viagens de hoje). `RoteiroFragment` nesta task é só um placeholder (mostra "nenhum roteiro em andamento" fixo); a implementação real é a Task 6.

**Files:**
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/MotoristaHomeActivity.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensFragment.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/RoteiroFragment.kt`
- Create: `app/src/main/res/layout/activity_motorista_home.xml`
- Create: `app/src/main/res/layout/fragment_viagens.xml`
- Create: `app/src/main/res/layout/fragment_roteiro.xml`
- Create: `app/src/main/res/menu/menu_bottom_nav.xml`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensAbertasActivity.kt`
- Delete: `app/src/main/res/layout/activity_viagens_abertas.xml`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/LoginActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ViagensAbertasViewModel`/`ViagemAbertaAdapter` (já existem, sem mudança).
- Produces: `MotoristaHomeActivity` como launcher pós-login; `RoteiroFragment` como classe existente que a Task 6 substitui por completo (Kotlin e layout).

- [ ] **Step 1: Adicionar a dependência `fragment-ktx` ao módulo `app`**

Em `app/build.gradle.kts`, dentro do bloco `dependencies { ... }`, logo abaixo de `implementation(libs.androidx.activity.ktx)`:
```kotlin
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
```
(O alias `libs.androidx.fragment.ktx` já existe em `gradle/libs.versions.toml` — usado pelo `gerencial`. Não precisa adicionar nada ao catálogo.)

- [ ] **Step 2: Menu do bottom navigation**

Criar `app/src/main/res/menu/menu_bottom_nav.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_viagens"
        android:title="@string/nav_viagens"
        android:icon="@android:drawable/ic_menu_agenda" />
    <item
        android:id="@+id/nav_roteiro"
        android:title="@string/nav_roteiro"
        android:icon="@android:drawable/ic_menu_directions" />
</menu>
```

- [ ] **Step 3: Layout do host**

Criar `app/src/main/res/layout/activity_motorista_home.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <FrameLayout
        android:id="@+id/containerFragmento"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:menu="@menu/menu_bottom_nav" />

</LinearLayout>
```

- [ ] **Step 4: `MotoristaHomeActivity`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/MotoristaHomeActivity.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.com.anderson.meuprimeiroprojeto.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Tela inicial do motorista: menu inferior com Viagens e Roteiro. */
class MotoristaHomeActivity : AppCompatActivity() {

    /**
     * Mostra o fragmento de Viagens ao abrir e liga a navegação inferior. O `savedInstanceState`
     * evita trocar o fragmento de novo numa recriação (ex.: giro de tela) — o FragmentManager já
     * restaura sozinho o que estava sendo exibido.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motorista_home)

        if (savedInstanceState == null) {
            mostrarFragmento(ViagensFragment())
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_viagens -> {
                    mostrarFragmento(ViagensFragment())
                    true
                }
                R.id.nav_roteiro -> {
                    mostrarFragmento(RoteiroFragment())
                    true
                }
                else -> false
            }
        }
    }

    /** Troca o fragmento exibido no container, usado pelos itens do menu inferior. */
    private fun mostrarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.containerFragmento, fragmento)
            .commit()
    }
}
```

- [ ] **Step 5: Layout da aba Viagens**

Criar `app/src/main/res/layout/fragment_viagens.xml` (igual ao `activity_viagens_abertas.xml` de hoje):
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/textBoasVindas"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textSize="18sp"
            tools:text="Bem-vindo, Fulano" />

        <Button
            android:id="@+id/botaoSair"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/viagens_sair" />
    </LinearLayout>

    <TextView
        android:id="@+id/textListaVazia"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="24dp"
        android:gravity="center"
        android:text="@string/viagens_lista_vazia"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerViagens"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="8dp" />

</LinearLayout>
```
Apagar `app/src/main/res/layout/activity_viagens_abertas.xml`.

- [ ] **Step 6: `ViagensFragment`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensFragment.kt` (mesma lógica que `ViagensAbertasActivity` tinha, adaptada pro ciclo de vida de Fragment — `viewLifecycleOwner` em vez de `this` na observação de LiveData, `requireContext()`/`view.findViewById` em vez de `this`/`findViewById` direto):
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasViewModel

/** Aba "Viagens": lista das viagens do motorista que ainda não entraram em nenhum roteiro. */
class ViagensFragment : Fragment(R.layout.fragment_viagens) {

    private val viewModel: ViagensAbertasViewModel by viewModels()

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.textBoasVindas).text =
            getString(R.string.viagens_boas_vindas, SessaoMotorista.nomeMotorista(requireContext()))

        view.findViewById<Button>(R.id.botaoSair).setOnClickListener {
            AutenticacaoRepositorio.sair()
            SessaoMotorista.sair(requireContext())
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        recyclerViagens = view.findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(requireContext())
        textListaVazia = view.findViewById(R.id.textListaVazia)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }
    }

    /** Recarrega a lista sempre que a aba volta a ficar visível (ex.: após voltar da tela de detalhes). */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    private fun exibirEstado(estado: ViagensAbertasUiState) {
        if (estado.erroAoCarregar) {
            textListaVazia.text = getString(R.string.viagens_erro_carregar)
            textListaVazia.visibility = View.VISIBLE
            recyclerViagens.visibility = View.GONE
        } else {
            recyclerViagens.adapter = ViagemAbertaAdapter(estado.viagens) { item ->
                val intent = Intent(requireContext(), DetalhesViagemActivity::class.java)
                    .putExtra(DetalhesViagemActivity.EXTRA_VIAGEM_ID, item.viagemId)
                startActivity(intent)
            }

            val listaVazia = estado.viagens.isEmpty()
            textListaVazia.text = getString(R.string.viagens_lista_vazia)
            textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
            recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
        }
    }
}
```
Apagar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensAbertasActivity.kt`.

- [ ] **Step 7: Layout da aba Roteiro (placeholder — a Task 6 substitui por completo)**

Criar `app/src/main/res/layout/fragment_roteiro.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView
        android:id="@+id/textRoteiroVazio"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="24dp"
        android:gravity="center"
        android:text="@string/roteiro_vazio" />

</LinearLayout>
```

- [ ] **Step 8: `RoteiroFragment` (placeholder)**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/RoteiroFragment.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import br.com.anderson.meuprimeiroprojeto.R
import androidx.fragment.app.Fragment

/**
 * Aba "Roteiro": roteiro atual do motorista. Por enquanto só mostra o estado vazio — a
 * implementação completa (mapa, lista de entregas, iniciar/encerrar) é a Task 6 deste plano.
 */
class RoteiroFragment : Fragment(R.layout.fragment_roteiro)
```

- [ ] **Step 9: `LoginActivity` passa a abrir `MotoristaHomeActivity`**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/LoginActivity.kt`, trocar toda ocorrência de `ViagensAbertasActivity::class.java` por `MotoristaHomeActivity::class.java` (é usado em 2 lugares — o método que abre a lista após login bem-sucedido, e a checagem de sessão já ativa no `onCreate`). Não renomear o método em si (`abrirListaDeViagens`) nem mudar mais nada no arquivo.

- [ ] **Step 10: Atualizar o Manifest**

Em `app/src/main/AndroidManifest.xml`, trocar:
```xml
        <activity
            android:name=".motorista.ui.ViagensAbertasActivity"
            android:exported="false" />
```
por:
```xml
        <activity
            android:name=".motorista.ui.MotoristaHomeActivity"
            android:exported="false" />
```
(`DetalhesViagemActivity` continua com sua própria entrada, sem mudança.)

- [ ] **Step 11: Novas strings**

Em `app/src/main/res/values/strings.xml`, acrescentar:
```xml
    <string name="nav_viagens">Viagens</string>
    <string name="nav_roteiro">Roteiro</string>
    <string name="roteiro_vazio">Nenhum roteiro em andamento. Toque numa viagem em Viagens para começar a montar um.</string>
```

- [ ] **Step 12: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: Teste manual no celular**

Login como motorista → confirmar que abre a tela com o menu inferior (Viagens/Roteiro) → aba Viagens mostra a mesma lista de sempre, com boas-vindas e botão sair funcionando → aba Roteiro mostra a mensagem de "nenhum roteiro em andamento" → tocar numa viagem ainda abre a tela de detalhes de sempre (ainda sem os botões novos — isso só chega na Task 5).

- [ ] **Step 14: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/menu/menu_bottom_nav.xml app/src/main/res/layout/activity_motorista_home.xml app/src/main/res/layout/fragment_viagens.xml app/src/main/res/layout/fragment_roteiro.xml app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/MotoristaHomeActivity.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensFragment.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/RoteiroFragment.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/LoginActivity.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "Reestrutura o app motorista com bottom navigation (abas Viagens e Roteiro)"
```

---

## Task 3: `RoteiroRepositorio` (motorista) + ajustes no `ViagemRepositorio` (motorista)

**Contexto:** a camada de dados que monta/gerencia o roteiro do motorista. Nada ainda chama essas funções (a UI consome nas Tasks 5 e 6) — esta task só cria a base, e compila sozinha.

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/RoteiroRepositorio.kt`

**Interfaces:**
- Consumes: `RoteiroRemota`, `StatusRoteiro` (Task 1).
- Produces: `ViagemRepositorio.buscarPorRoteiro(roteiroId): List<Pair<String, ViagemRemota>>`; `RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid): Pair<String, RoteiroRemota>?`, `.adicionarViagem(viagemId, motoristaUid): String`, `.removerViagem(viagemId, roteiroId)`, `.iniciarRoteiro(roteiroId, viagemIds)`, `.marcarEntregue(viagemId)`, `.encerrarRoteiro(roteiroId, kmTotalRota, viagens, distanciasPorPedido)`. Exceções: `RoteiroRepositorio.CaminhaoIncompativelException`, `RoteiroRepositorio.RoteiroJaIniciadoException`.

- [ ] **Step 1: `ViagemRepositorio` ganha `buscarAbertasDoMotorista` filtrando sem roteiro, e `buscarPorRoteiro`**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt`, substituir o método `buscarAbertasDoMotorista` por (mesma consulta ao Firestore, só ganha um filtro em Kotlin depois — uma viagem `EM_ROTA` sempre tem `roteiroId` preenchido, já que só entra em rota junto com o roteiro, então o filtro abaixo já exclui automaticamente as que estão em rota, sem precisar mudar a consulta):
```kotlin
    /**
     * Viagens do motorista ainda disponíveis pra montar um roteiro: atribuídas e ainda sem
     * roteiro. Uma viagem em rota sempre tem `roteiroId` preenchido (só entra em rota junto com
     * o roteiro dela), então o filtro abaixo já a exclui automaticamente.
     */
    suspend fun buscarAbertasDoMotorista(motoristaUid: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereIn("status", listOf(StatusViagem.ATRIBUIDA.name, StatusViagem.EM_ROTA.name))
            .get()
            .aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
            documento.id to remota
        }.filter { (_, viagem) -> viagem.roteiroId == null }
            .sortedBy { it.second.dataAtribuicao }
    }

    /** Viagens de um roteiro específico — usado pela aba Roteiro e ao encerrar o roteiro. */
    suspend fun buscarPorRoteiro(roteiroId: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("roteiroId", roteiroId)
            .get()
            .aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
            documento.id to remota
        }.sortedBy { it.second.dataAtribuicao }
    }
```

- [ ] **Step 2: `RoteiroRepositorio`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/RoteiroRepositorio.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.backend

import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Monta e conduz o roteiro do motorista: adicionar/remover viagem (enquanto planejando),
 * iniciar, marcar entrega e encerrar (com o cálculo do rateio de km). Só o próprio motorista
 * mexe no seu roteiro — sem concorrência entre motoristas diferentes, então as escritas aqui não
 * precisam de transação (diferente de "atribuir viagem" no gerencial, que pode ter dois gerentes
 * mexendo no saldo do mesmo pedido ao mesmo tempo).
 */
object RoteiroRepositorio {

    private val db get() = Firebase.firestore

    /** Lançada ao tentar adicionar uma viagem com caminhão diferente das já presentes no roteiro. */
    class CaminhaoIncompativelException :
        Exception("Esta viagem usa um caminhão diferente do roteiro atual")

    /** Lançada ao tentar adicionar/remover viagem de um roteiro que já foi iniciado. */
    class RoteiroJaIniciadoException :
        Exception("Não é possível alterar as viagens de um roteiro já iniciado")

    /** Busca o roteiro ativo do motorista (planejando ou em rota) — só pode haver um por vez. */
    suspend fun buscarRoteiroAtivo(motoristaUid: String): Pair<String, RoteiroRemota>? {
        val snapshot = db.collection("roteiros")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereIn("status", listOf(StatusRoteiro.PLANEJANDO.name, StatusRoteiro.EM_ROTA.name))
            .get()
            .aguardar()
        val documento = snapshot.documents.firstOrNull() ?: return null
        val remoto = documento.toObject(RoteiroRemota::class.java) ?: return null
        return documento.id to remoto
    }

    /** Busca um roteiro específico pelo id do documento. */
    suspend fun buscarPorId(roteiroId: String): RoteiroRemota? {
        val documento = db.collection("roteiros").document(roteiroId).get().aguardar()
        return documento.toObject(RoteiroRemota::class.java)
    }

    /**
     * Adiciona a viagem ao roteiro ativo do motorista, criando um roteiro novo (PLANEJANDO) se
     * ele ainda não tiver nenhum. Precisa de conexão na hora: lê o roteiro/as viagens já nele
     * antes de decidir (valida caminhão consistente), diferente das escritas "cegas" como
     * iniciar/marcar entregue. Retorna o id do roteiro (novo ou existente).
     */
    suspend fun adicionarViagem(viagemId: String, motoristaUid: String): String {
        val viagem = ViagemRepositorio.buscarPorId(viagemId)
            ?: throw IllegalStateException("Viagem não encontrada")

        val roteiroExistente = buscarRoteiroAtivo(motoristaUid)
        val (roteiroId, roteiro) = if (roteiroExistente != null) {
            roteiroExistente
        } else {
            val novoRef = db.collection("roteiros").document()
            val novoRoteiro = RoteiroRemota(
                motoristaUid = motoristaUid,
                motoristaNome = viagem.motoristaNome,
                status = StatusRoteiro.PLANEJANDO.name
            )
            novoRef.set(novoRoteiro).aguardar()
            novoRef.id to novoRoteiro
        }

        if (roteiro.status != StatusRoteiro.PLANEJANDO.name) throw RoteiroJaIniciadoException()

        val viagensNoRoteiro = ViagemRepositorio.buscarPorRoteiro(roteiroId)
        val caminhaoDoRoteiro = viagensNoRoteiro.firstOrNull()?.second?.caminhaoPlaca
        if (caminhaoDoRoteiro != null && caminhaoDoRoteiro != viagem.caminhaoPlaca) {
            throw CaminhaoIncompativelException()
        }

        db.collection("viagens").document(viagemId).update("roteiroId", roteiroId).aguardar()
        return roteiroId
    }

    /**
     * Remove a viagem do roteiro (só funciona enquanto ele está PLANEJANDO). Se essa era a
     * última viagem do roteiro, apaga o próprio documento do roteiro (evita roteiro vazio órfão).
     */
    suspend fun removerViagem(viagemId: String, roteiroId: String) {
        val roteiro = buscarPorId(roteiroId)
        if (roteiro?.status != StatusRoteiro.PLANEJANDO.name) throw RoteiroJaIniciadoException()

        val viagensNoRoteiro = ViagemRepositorio.buscarPorRoteiro(roteiroId)
        db.collection("viagens").document(viagemId).update("roteiroId", null).aguardar()

        if (viagensNoRoteiro.size <= 1) {
            db.collection("roteiros").document(roteiroId).delete().aguardar()
        }
    }

    /**
     * Inicia o roteiro: muda o status dele e de todas as suas viagens pra EM_ROTA de uma vez,
     * numa escrita em lote (WriteBatch). Não espera confirmação do servidor — mesmo raciocínio
     * de sempre, funciona offline.
     */
    fun iniciarRoteiro(roteiroId: String, viagemIds: List<String>) {
        val agora = System.currentTimeMillis()
        val lote = db.batch()
        lote.update(
            db.collection("roteiros").document(roteiroId),
            mapOf("status" to StatusRoteiro.EM_ROTA.name, "dataInicio" to agora)
        )
        for (viagemId in viagemIds) {
            lote.update(
                db.collection("viagens").document(viagemId),
                mapOf("status" to StatusViagem.EM_ROTA.name, "dataInicio" to agora)
            )
        }
        lote.commit()
    }

    /** Marca uma entrega como concluída. Escrita direta, sem esperar confirmação do servidor. */
    fun marcarEntregue(viagemId: String) {
        db.collection("viagens").document(viagemId)
            .update(
                mapOf(
                    "status" to StatusViagem.CONCLUIDA.name,
                    "dataFim" to System.currentTimeMillis()
                )
            )
    }

    /**
     * Encerra o roteiro: rateia o [kmTotalRota] entre as viagens CONCLUIDA por peso
     * (volume × distância dedicada do pedido) — canceladas ficam de fora do rateio (peso zero).
     * A última viagem (por `dataAtribuicao`, ordem estável) recebe o resto da divisão, garantindo
     * que a soma dos km atribuídos seja exatamente igual a [kmTotalRota] mesmo com arredondamento
     * de ponto flutuante. [viagens] e [distanciasPorPedido] já vêm prontos de quem chamou (a
     * ViewModel, que tem acesso ao Room pra calcular [kmTotalRota] via Haversine).
     */
    suspend fun encerrarRoteiro(
        roteiroId: String,
        kmTotalRota: Double,
        viagens: List<Pair<String, ViagemRemota>>,
        distanciasPorPedido: Map<Int, Double>
    ) {
        val concluidas = viagens
            .filter { (_, viagem) -> viagem.status == StatusViagem.CONCLUIDA.name }
            .sortedBy { (_, viagem) -> viagem.dataAtribuicao }

        val lote = db.batch()

        if (concluidas.isNotEmpty()) {
            val pesos = concluidas.map { (id, viagem) ->
                val distancia = distanciasPorPedido[viagem.pedidoId] ?: 0.0
                Triple(id, viagem, viagem.metrosCubicos * distancia)
            }
            val pesoTotal = pesos.sumOf { it.third }

            if (pesoTotal > 0.0) {
                var somaParcial = 0.0
                for (i in pesos.indices) {
                    val (viagemId, _, peso) = pesos[i]
                    val kmAtribuido = if (i == pesos.lastIndex) {
                        kmTotalRota - somaParcial
                    } else {
                        (kmTotalRota * (peso / pesoTotal)).also { somaParcial += it }
                    }
                    lote.update(db.collection("viagens").document(viagemId), "kmPercorridos", kmAtribuido)
                }
            }
        }

        lote.update(
            db.collection("roteiros").document(roteiroId),
            mapOf(
                "status" to StatusRoteiro.CONCLUIDO.name,
                "dataFim" to System.currentTimeMillis(),
                "kmTotalRota" to kmTotalRota
            )
        )
        lote.commit().aguardar()
    }
}
```

- [ ] **Step 3: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (nada ainda chama essas funções novas, mas o arquivo compila sozinho).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/RoteiroRepositorio.kt
git commit -m "Adiciona RoteiroRepositorio (motorista): montar, iniciar, marcar entrega e encerrar roteiro com rateio de km"
```

---

## Task 4: `RastreamentoService` + `PontoGps` (Room) — rastreamento por roteiro

**Contexto:** desde a branch anterior, o serviço rastreava um conjunto de viagens simultâneas (`viagensAtivas: Set<String>`). Como só pode haver um roteiro ativo por motorista agora, essa complexidade não é mais necessária — volta a ser um único alvo (`roteiroAtivo: String?`), só que agora é o roteiro, não mais uma viagem. Os pontos de GPS passam a pertencer ao roteiro: `PontoGps.viagemId` vira `PontoGps.roteiroId`, e a sincronização escreve em `roteiros/{id}/pontosGps` (antes era `viagens/{id}/pontosGps`) e atualiza `roteiros/{id}.ultimaLatitude/ultimaLongitude/ultimoHorario` (antes era no documento da viagem). Como `MotoristaHomeActivity` já existe (Task 2), a notificação do serviço já pode referenciar essa Activity diretamente, sem depender de nenhuma task futura.

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGps.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsDao.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/AppDatabase.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsRepositorio.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/gps/RastreamentoService.kt`

**Interfaces:**
- Produces: `RastreamentoService.iniciar(context, roteiroId)`/`.encerrar(context, roteiroId)` (mesma forma de antes, parâmetro renomeado); `PontoGpsRepositorio.calcularKmPercorridos(context, roteiroId): Double` (mesma fórmula, parâmetro renomeado); `PontoGpsDao.buscarPorRoteiro(roteiroId): List<PontoGps>` (renomeado de `buscarPorViagem`).

- [ ] **Step 1: `PontoGps` — `viagemId` vira `roteiroId`**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGps.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um ponto de GPS capturado durante um roteiro em rota. Fica no banco local primeiro
 * (fila offline) e [sincronizado] só vira true depois de confirmado no Firestore.
 * roteiroId é o id do documento do roteiro no Firestore (não é mais gerado pelo Room).
 */
@Entity(tableName = "pontos_gps")
data class PontoGps(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roteiroId: String,
    val latitude: Double,
    val longitude: Double,
    val horario: Long,
    val sincronizado: Boolean = false
)
```

- [ ] **Step 2: `PontoGpsDao` — renomeia a consulta**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsDao.kt`, o método `buscarPorViagem` vira:
```kotlin
    /** Usado depois (mapa/histórico) para desenhar o trajeto do roteiro. */
    @Query("SELECT * FROM pontos_gps WHERE roteiroId = :roteiroId ORDER BY horario")
    suspend fun buscarPorRoteiro(roteiroId: String): List<PontoGps>
```
(Resto do arquivo — `inserir`, `buscarPendentes`, `marcarSincronizado` — sem mudanças.)

- [ ] **Step 3: `AppDatabase` — sobe a versão**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/AppDatabase.kt`, a anotação `@Database` vira:
```kotlin
@Database(entities = [PontoGps::class], version = 4, exportSchema = false)
```
(`fallbackToDestructiveMigration(true)` já está configurado — não precisa mexer em mais nada; o banco local é recriado na próxima abertura do app, perdendo só a fila offline de pontos ainda não sincronizados, que é aceitável nesta fase do projeto, como o comentário já existente no arquivo explica.)

- [ ] **Step 4: `PontoGpsRepositorio` — renomeia o parâmetro**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsRepositorio.kt`, substituir o conteúdo completo por:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.db

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Cálculo do km percorrido a partir dos pontos de GPS gravados localmente. */
object PontoGpsRepositorio {

    private const val RAIO_TERRA_METROS = 6_371_000.0

    /**
     * Soma a distância (fórmula de Haversine) entre cada par de pontos consecutivos do roteiro,
     * usando os pontos já salvos localmente (Room) — não depende de terem sincronizado com o
     * Firestore. Recalculado sob demanda: com um ponto a cada 10s, mesmo um roteiro de horas tem
     * no máximo algumas centenas de pontos, então somar tudo de novo é instantâneo.
     */
    suspend fun calcularKmPercorridos(context: Context, roteiroId: String): Double {
        val pontos = AppDatabase.obter(context).pontoGpsDao().buscarPorRoteiro(roteiroId)
        if (pontos.size < 2) return 0.0

        var totalMetros = 0.0
        for (i in 1 until pontos.size) {
            totalMetros += distanciaEmMetros(
                pontos[i - 1].latitude, pontos[i - 1].longitude,
                pontos[i].latitude, pontos[i].longitude
            )
        }
        return totalMetros / 1000.0
    }

    /** Distância em linha reta entre duas coordenadas na superfície da Terra (fórmula de Haversine). */
    private fun distanciaEmMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RAIO_TERRA_METROS * c
    }
}
```

- [ ] **Step 5: `RastreamentoService` — reescreve para um único roteiro ativo**

Substituir o conteúdo completo de `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/gps/RastreamentoService.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.gps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.aguardar
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.ui.MotoristaHomeActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que grava pontos de GPS do roteiro em rota. Só existe um roteiro
 * ativo por motorista por vez, então (diferente da versão anterior deste serviço, que rastreava
 * várias viagens simultâneas) volta a ser um único alvo — mais simples.
 */
class RastreamentoService : Service() {

    companion object {
        const val EXTRA_ROTEIRO_ID = "roteiro_id"
        private const val ACTION_INICIAR = "br.com.anderson.meuprimeiroprojeto.INICIAR_ROTEIRO"
        private const val ACTION_ENCERRAR = "br.com.anderson.meuprimeiroprojeto.ENCERRAR_ROTEIRO"
        private const val CANAL_ID = "rastreamento_viagem"
        private const val NOTIFICACAO_ID = 1001
        private const val INTERVALO_ATUALIZACAO_MS = 10_000L
        private const val TAG = "RastreamentoService"

        /** Começa a rastrear este roteiro. */
        fun iniciar(context: Context, roteiroId: String) {
            val intent = Intent(context, RastreamentoService::class.java)
                .setAction(ACTION_INICIAR)
                .putExtra(EXTRA_ROTEIRO_ID, roteiroId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Para de rastrear este roteiro. */
        fun encerrar(context: Context, roteiroId: String) {
            val intent = Intent(context, RastreamentoService::class.java)
                .setAction(ACTION_ENCERRAR)
                .putExtra(EXTRA_ROTEIRO_ID, roteiroId)
            context.startService(intent)
        }
    }

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clienteLocalizacao: FusedLocationProviderClient
    private var callbackLocalizacao: LocationCallback? = null
    private var roteiroAtivo: String? = null

    /** Prepara o cliente de localização (FusedLocationProviderClient) quando o serviço é criado. */
    override fun onCreate() {
        super.onCreate()
        clienteLocalizacao = LocationServices.getFusedLocationProviderClient(this)
    }

    /**
     * START_STICKY: se o sistema matar o processo por falta de memória, o Android tenta recriar
     * o serviço (o Intent original pode vir nulo nesse caso — perde o [roteiroAtivo] em memória;
     * quem recupera isso é a tela de detalhe do roteiro, chamando [iniciar] de novo ao reabrir um
     * roteiro que já está EM_ROTA).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roteiroId = intent?.getStringExtra(EXTRA_ROTEIRO_ID)
        if (roteiroId.isNullOrBlank()) {
            if (roteiroAtivo == null) stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_ENCERRAR) {
            if (roteiroAtivo == roteiroId) {
                roteiroAtivo = null
                callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        roteiroAtivo = roteiroId
        criarCanalDeNotificacao()
        startForeground(NOTIFICACAO_ID, criarNotificacao(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        iniciarAtualizacoesDeLocalizacao()
        return START_STICKY
    }

    /**
     * Registra o callback do FusedLocationProviderClient para receber posições a cada
     * [INTERVALO_ATUALIZACAO_MS]; cada posição recebida é gravada no Room e depois dispara a
     * sincronização da fila offline e a atualização da posição atual no Firestore.
     */
    private fun iniciarAtualizacoesDeLocalizacao() {
        val temPermissao = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!temPermissao) {
            stopSelf()
            return
        }

        // Se já havia um registro anterior (reinício do serviço), encerra antes de criar outro.
        callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }

        val alvoRoteiroId = roteiroAtivo ?: return
        val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_ATUALIZACAO_MS).build()
        val callback = object : LocationCallback() {
            /** Grava a posição recebida no Room e atualiza o Firestore. */
            override fun onLocationResult(resultado: LocationResult) {
                val localizacao = resultado.lastLocation ?: return
                val horario = System.currentTimeMillis()
                escopo.launch {
                    AppDatabase.obter(applicationContext).pontoGpsDao().inserir(
                        PontoGps(
                            roteiroId = alvoRoteiroId,
                            latitude = localizacao.latitude,
                            longitude = localizacao.longitude,
                            horario = horario
                        )
                    )
                    atualizarPosicaoAtual(alvoRoteiroId, localizacao.latitude, localizacao.longitude, horario)
                    sincronizarPontosPendentes()
                }
            }
        }
        callbackLocalizacao = callback
        clienteLocalizacao.requestLocationUpdates(pedido, callback, Looper.getMainLooper())
    }

    /**
     * A fila offline: tenta subir para o Firestore os pontos que ainda não foram sincronizados.
     * Cada ponto é tentado de forma independente — se um falhar (sem conexão, erro do servidor
     * etc.) os outros pendentes continuam sendo tentados. O ponto que falhou continua no Room
     * como pendente e entra na fila de novo na próxima atualização (10 em 10s).
     */
    private suspend fun sincronizarPontosPendentes() {
        val dao = AppDatabase.obter(applicationContext).pontoGpsDao()
        for (ponto in dao.buscarPendentes()) {
            try {
                Firebase.firestore.collection("roteiros").document(ponto.roteiroId)
                    .collection("pontosGps").document("ponto_${ponto.id}")
                    .set(
                        mapOf(
                            "latitude" to ponto.latitude,
                            "longitude" to ponto.longitude,
                            "horario" to ponto.horario
                        )
                    )
                    .aguardar()
                dao.marcarSincronizado(ponto.id)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao sincronizar ponto ${ponto.id} do roteiro ${ponto.roteiroId}", erro)
            }
        }
    }

    /**
     * Atualiza a posição atual no próprio documento do roteiro — é o que o mapa em tempo real
     * do app gerencial escuta. Separado da fila offline de propósito: se falhar por falta de
     * conexão não tem problema guardar pra depois, a próxima atualização (10s) já traz uma
     * posição mais nova mesmo.
     */
    private suspend fun atualizarPosicaoAtual(roteiroId: String, latitude: Double, longitude: Double, horario: Long) {
        try {
            Firebase.firestore.collection("roteiros").document(roteiroId)
                .update(
                    mapOf(
                        "ultimaLatitude" to latitude,
                        "ultimaLongitude" to longitude,
                        "ultimoHorario" to horario
                    )
                )
                .aguardar()
        } catch (erro: Exception) {
            // sem conexão agora; a próxima atualização de localização tenta de novo
        }
    }

    /** Canal exigido a partir do Android 8 para mostrar a notificação fixa do serviço. */
    private fun criarCanalDeNotificacao() {
        val gerenciador = getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(
            CANAL_ID, getString(R.string.notificacao_rastreamento_canal), NotificationManager.IMPORTANCE_LOW
        )
        gerenciador.createNotificationChannel(canal)
    }

    /** Notificação fixa (ongoing) que o foreground service precisa manter visível enquanto roda. */
    private fun criarNotificacao(): Notification {
        val intentAbrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MotoristaHomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(getString(R.string.notificacao_rastreamento_titulo))
            .setContentText(getString(R.string.notificacao_rastreamento_texto))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(intentAbrirApp)
            .setOngoing(true)
            .build()
    }

    /** Para de receber localizações e cancela as coroutines pendentes ao destruir o serviço. */
    override fun onDestroy() {
        super.onDestroy()
        callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }
        escopo.cancel()
    }

    /** Serviço não usa binding (é iniciado com startService/startForegroundService, não bind). */
    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 6: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGps.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsDao.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/AppDatabase.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsRepositorio.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/gps/RastreamentoService.kt
git commit -m "RastreamentoService e pontos de GPS passam a ser por roteiro, não por viagem"
```

---

## Task 5: `DetalhesViagemActivity` + `DetalhesViagemViewModel` — botão contextual (adicionar/remover/marcar entregue)

**Contexto:** a tela de detalhe de uma viagem perde o mapa próprio (o trajeto ao vivo agora é por roteiro, mostrado na aba Roteiro — Task 6) e ganha um botão cuja ação muda conforme o contexto: "Adicionar ao roteiro" (viagem sem roteiro, e o motorista não tem roteiro em rota agora), "Remover do roteiro" (viagem já num roteiro ainda planejando), "Marcar como entregue" (viagem num roteiro em rota, ainda não concluída), ou nenhum botão (viagem já resolvida, ou roteiro em rota que não é o dela).

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/DetalhesViagemViewModel.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/DetalhesViagemActivity.kt`
- Modify: `app/src/main/res/layout/activity_detalhes_viagem.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RoteiroRepositorio.buscarRoteiroAtivo/buscarPorId/adicionarViagem/removerViagem/marcarEntregue` (Task 3).
- Produces: nada consumido por outra task deste plano.

- [ ] **Step 1: `DetalhesViagemViewModel` — reescreve**

Substituir o conteúdo completo de `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/DetalhesViagemViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import kotlinx.coroutines.launch

/** Ação disponível no botão da tela de detalhes, conforme o estado da viagem/roteiro. */
enum class AcaoViagem { ADICIONAR_AO_ROTEIRO, REMOVER_DO_ROTEIRO, MARCAR_ENTREGUE, NENHUMA }

/** Estado exibido por [DetalhesViagemActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.DetalhesViagemActivity]. */
data class DetalhesViagemUiState(
    val carregado: Boolean = false,
    val erroAoCarregar: Boolean = false,
    val destinatarioNome: String = "",
    val enderecoResumido: String = "",
    val metrosCubicos: Double = 0.0,
    val caminhaoPlaca: String = "",
    val status: String = "",
    val roteiroId: String? = null,
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double? = null,
    val acao: AcaoViagem = AcaoViagem.NENHUMA,
    val erroAcao: Evento<String>? = null,
    val acaoConcluida: Evento<Unit>? = null
)

/**
 * Carrega os dados da viagem/pedido e decide qual ação o botão oferece — ver [AcaoViagem]. Sem
 * mapa/Room aqui: o trajeto ao vivo e o cálculo de km vivem na aba Roteiro agora.
 */
class DetalhesViagemViewModel : ViewModel() {

    private val _estado = MutableLiveData(DetalhesViagemUiState())
    val estado: LiveData<DetalhesViagemUiState> = _estado

    private lateinit var motoristaUid: String

    fun carregar(viagemId: String, motoristaUid: String) {
        this.motoristaUid = motoristaUid
        viewModelScope.launch {
            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                null
            }
            if (viagem == null) {
                _estado.value = _estado.value?.copy(erroAoCarregar = true)
                return@launch
            }

            val pedido = try {
                PedidoRepositorio.buscarPorId(viagem.pedidoId)
            } catch (erro: Exception) {
                null
            }

            val acao = decidirAcao(viagem, motoristaUid)

            _estado.value = DetalhesViagemUiState(
                carregado = true,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                enderecoResumido = pedido?.enderecoResumido() ?: "",
                metrosCubicos = viagem.metrosCubicos,
                caminhaoPlaca = viagem.caminhaoPlaca,
                status = viagem.status,
                roteiroId = viagem.roteiroId,
                dataInicio = viagem.dataInicio,
                dataFim = viagem.dataFim,
                kmPercorridos = viagem.kmPercorridos,
                acao = acao
            )
        }
    }

    /**
     * Sem roteiro ainda: oferece "adicionar" só se o motorista não tiver roteiro em rota agora
     * (roteiro só aceita viagem nova enquanto planejando). Com roteiro: "remover" se ainda
     * planejando, "marcar entregue" se em rota e a viagem ainda não foi concluída, nada se já
     * concluída/cancelada.
     */
    private suspend fun decidirAcao(viagem: ViagemRemota, motoristaUid: String): AcaoViagem {
        if (viagem.status == StatusViagem.CONCLUIDA.name || viagem.status == StatusViagem.CANCELADA.name) {
            return AcaoViagem.NENHUMA
        }
        val roteiroId = viagem.roteiroId
        if (roteiroId == null) {
            val roteiroAtivo = try {
                RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid)
            } catch (erro: Exception) {
                null
            }
            return if (roteiroAtivo?.second?.status == StatusRoteiro.EM_ROTA.name) {
                AcaoViagem.NENHUMA
            } else {
                AcaoViagem.ADICIONAR_AO_ROTEIRO
            }
        }
        val roteiro = try {
            RoteiroRepositorio.buscarPorId(roteiroId)
        } catch (erro: Exception) {
            null
        }
        return when (roteiro?.status) {
            StatusRoteiro.PLANEJANDO.name -> AcaoViagem.REMOVER_DO_ROTEIRO
            StatusRoteiro.EM_ROTA.name -> AcaoViagem.MARCAR_ENTREGUE
            else -> AcaoViagem.NENHUMA
        }
    }

    fun adicionarAoRoteiro(viagemId: String) {
        viewModelScope.launch {
            try {
                RoteiroRepositorio.adicionarViagem(viagemId, motoristaUid)
                _estado.value = _estado.value?.copy(acaoConcluida = Evento(Unit))
            } catch (erro: RoteiroRepositorio.CaminhaoIncompativelException) {
                _estado.value = _estado.value?.copy(erroAcao = Evento(erro.message ?: ""))
            } catch (erro: RoteiroRepositorio.RoteiroJaIniciadoException) {
                _estado.value = _estado.value?.copy(erroAcao = Evento(erro.message ?: ""))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erroAcao = Evento("Sem conexão. Tente de novo"))
            }
        }
    }

    fun removerDoRoteiro(viagemId: String, roteiroId: String) {
        viewModelScope.launch {
            try {
                RoteiroRepositorio.removerViagem(viagemId, roteiroId)
                _estado.value = _estado.value?.copy(acaoConcluida = Evento(Unit))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erroAcao = Evento("Sem conexão. Tente de novo"))
            }
        }
    }

    /** Não espera confirmação do servidor — mesmo raciocínio das escritas simples do motorista. */
    fun marcarEntregue(viagemId: String) {
        RoteiroRepositorio.marcarEntregue(viagemId)
        _estado.value = _estado.value?.copy(acaoConcluida = Evento(Unit))
    }
}
```

- [ ] **Step 2: `DetalhesViagemActivity` — reescreve**

Substituir o conteúdo completo de `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/DetalhesViagemActivity.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.formatarKm
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.AcaoViagem
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de detalhe de uma viagem: dados do pedido e um botão cuja ação muda conforme o contexto
 * (adicionar/remover do roteiro, marcar como entregue). Sem mapa próprio — o trajeto ao vivo e o
 * km calculado vivem na aba Roteiro, já que os pontos de GPS agora pertencem ao roteiro inteiro.
 */
class DetalhesViagemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private val viewModel: DetalhesViagemViewModel by viewModels()
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private lateinit var viagemId: String
    private lateinit var textPeriodo: TextView
    private lateinit var textKm: TextView
    private lateinit var botaoAcao: Button
    private lateinit var textErro: TextView
    private var estadoAtual: DetalhesViagemUiState = DetalhesViagemUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_viagem)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        textPeriodo = findViewById(R.id.textPeriodo)
        textKm = findViewById(R.id.textKm)
        botaoAcao = findViewById(R.id.botaoAcao)
        textErro = findViewById(R.id.textErro)

        botaoAcao.setOnClickListener { executarAcao() }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(viagemId, uid)
    }

    private fun exibirEstado(estado: DetalhesViagemUiState) {
        estadoAtual = estado
        if (estado.erroAoCarregar) {
            mostrarErro(getString(R.string.iniciar_transito_erro_carregar))
            return
        }
        if (!estado.carregado) return

        findViewById<TextView>(R.id.textDestinatario).text = estado.destinatarioNome
        findViewById<TextView>(R.id.textEndereco).text = estado.enderecoResumido
        findViewById<TextView>(R.id.textMetrosCubicos).text = getString(
            R.string.iniciar_transito_m3_formato, formatarM3(estado.metrosCubicos)
        )
        findViewById<TextView>(R.id.textCaminhao).text = getString(
            R.string.iniciar_transito_caminhao_formato, estado.caminhaoPlaca
        )

        if (estado.dataInicio != null) {
            textPeriodo.text = getString(
                R.string.detalhes_viagem_inicio_formato, formatoData.format(Date(estado.dataInicio))
            )
            textPeriodo.visibility = View.VISIBLE
        } else {
            textPeriodo.visibility = View.GONE
        }

        if (estado.kmPercorridos != null) {
            textKm.text = getString(R.string.detalhes_viagem_km_formato, formatarKm(estado.kmPercorridos))
            textKm.visibility = View.VISIBLE
        } else {
            textKm.visibility = View.GONE
        }

        when (estado.acao) {
            AcaoViagem.ADICIONAR_AO_ROTEIRO -> {
                botaoAcao.text = getString(R.string.detalhes_viagem_botao_adicionar)
                botaoAcao.visibility = View.VISIBLE
            }
            AcaoViagem.REMOVER_DO_ROTEIRO -> {
                botaoAcao.text = getString(R.string.detalhes_viagem_botao_remover)
                botaoAcao.visibility = View.VISIBLE
            }
            AcaoViagem.MARCAR_ENTREGUE -> {
                botaoAcao.text = getString(R.string.detalhes_viagem_botao_entregue)
                botaoAcao.visibility = View.VISIBLE
            }
            AcaoViagem.NENHUMA -> botaoAcao.visibility = View.GONE
        }

        estado.erroAcao?.getConteudoSeNaoTratado()?.let { mensagem ->
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
        }
        estado.acaoConcluida?.getConteudoSeNaoTratado()?.let {
            finish()
        }
    }

    private fun executarAcao() {
        when (estadoAtual.acao) {
            AcaoViagem.ADICIONAR_AO_ROTEIRO -> viewModel.adicionarAoRoteiro(viagemId)
            AcaoViagem.REMOVER_DO_ROTEIRO -> {
                val roteiroId = estadoAtual.roteiroId ?: return
                viewModel.removerDoRoteiro(viagemId, roteiroId)
            }
            AcaoViagem.MARCAR_ENTREGUE -> viewModel.marcarEntregue(viagemId)
            AcaoViagem.NENHUMA -> {}
        }
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
```

- [ ] **Step 3: Layout — remove mapa, bateria e avisos de permissão**

Substituir o conteúdo completo de `app/src/main/res/layout/activity_detalhes_viagem.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/textDestinatario"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/textEndereco"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp" />

        <TextView
            android:id="@+id/textMetrosCubicos"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp" />

        <TextView
            android:id="@+id/textCaminhao"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp" />

        <TextView
            android:id="@+id/textPeriodo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/textKm"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textStyle="bold"
            android:visibility="gone" />

        <Button
            android:id="@+id/botaoAcao"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp" />

        <TextView
            android:id="@+id/textErro"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="#B00020"
            android:gravity="center"
            android:visibility="gone" />

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 4: Strings — novas e remoção das órfãs**

Em `app/src/main/res/values/strings.xml`, acrescentar (perto de `detalhes_viagem_km_formato`):
```xml
    <string name="detalhes_viagem_botao_adicionar">Adicionar ao roteiro</string>
    <string name="detalhes_viagem_botao_remover">Remover do roteiro</string>
    <string name="detalhes_viagem_botao_entregue">Marcar como entregue</string>
```

Depois, **usando `grep` no código-fonte pra confirmar cada uma antes de apagar** (o mapa, os diálogos de confirmar iniciar/encerrar, o aviso de permissão e o painel de bateria saíram desta tela e não têm mais nenhum outro consumidor no módulo `app`), remover estas entradas de `strings.xml` se o grep confirmar zero referências fora do próprio arquivo de strings:
- `detalhes_viagem_posicao_atual`
- `detalhes_viagem_confirmar_iniciar`
- `detalhes_viagem_confirmar_encerrar`
- `detalhes_viagem_botao_encerrar`
- `detalhes_viagem_sem_pontos`
- `andamento_permissao_negada`
- `andamento_bateria_texto`
- `andamento_bateria_botao`
- `andamento_viagem_finalizada`
- `andamento_viagem_cancelada`

Se o grep encontrar alguma dessas ainda referenciada em algum `.kt`/`.xml` do módulo `app`, **não apagar aquela entrada específica** — manter e registrar isso no relatório da task.

- [ ] **Step 5: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Teste manual no celular**

Abrir uma viagem sem roteiro → botão "Adicionar ao roteiro" → confirmar que aparece "Remover do roteiro" ao reabrir a mesma viagem → tocar em "Remover do roteiro" → confirmar que volta a mostrar "Adicionar ao roteiro". (O fluxo de "Marcar como entregue" só é testável de ponta a ponta depois da Task 6, que implementa "Iniciar roteiro".)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/DetalhesViagemViewModel.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/DetalhesViagemActivity.kt app/src/main/res/layout/activity_detalhes_viagem.xml app/src/main/res/values/strings.xml
git commit -m "DetalhesViagemActivity: botão contextual (adicionar/remover do roteiro, marcar entregue), sem mapa próprio"
```

---

## Task 6: `RoteiroFragment` (implementação real) + `RoteiroViewModel`

**Contexto:** substitui por completo o placeholder da Task 2. Mostra: nenhum roteiro (mensagem vazia); roteiro planejando (lista das viagens adicionadas + botão "Iniciar roteiro", habilitado só com 1+ viagem); roteiro em rota (mapa com o trajeto percorrido até agora + lista com pendente/entregue + botão "Marcar como entregue" por item + botão "Encerrar roteiro", habilitado só quando todas as viagens estiverem resolvidas).

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/RoteiroFragment.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/RoteiroViewModel.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemDoRoteiroAdapter.kt`
- Modify: `app/src/main/res/layout/fragment_roteiro.xml`
- Create: `app/src/main/res/layout/item_viagem_do_roteiro.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RoteiroRepositorio` (Task 3), `RastreamentoService.iniciar/encerrar` (Task 4), `PontoGpsRepositorio.calcularKmPercorridos` (Task 4), `PontoGpsDao.buscarPorRoteiro` (Task 4).

- [ ] **Step 1: `RoteiroViewModel`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/RoteiroViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGpsRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import kotlinx.coroutines.launch

/** Uma viagem do roteiro atual, já com o nome do destinatário resolvido. */
data class ViagemDoRoteiro(val viagemId: String, val viagem: ViagemRemota, val destinatarioNome: String)

/** Estado exibido por [RoteiroFragment][br.com.anderson.meuprimeiroprojeto.motorista.ui.RoteiroFragment]. */
data class RoteiroUiState(
    val carregado: Boolean = false,
    val temRoteiro: Boolean = false,
    val roteiroId: String? = null,
    val status: String = "",
    val viagens: List<ViagemDoRoteiro> = emptyList(),
    val pontos: List<PontoGps> = emptyList(),
    val podeIniciar: Boolean = false,
    val podeEncerrar: Boolean = false,
    val erro: Evento<String>? = null,
    val roteiroEncerrado: Evento<Unit>? = null
)

/**
 * Estado do roteiro atual do motorista. É [AndroidViewModel] porque precisa do Room (pontos de
 * GPS) e do Context pra chamar o [RastreamentoService].
 */
class RoteiroViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(RoteiroUiState())
    val estado: LiveData<RoteiroUiState> = _estado

    private lateinit var motoristaUid: String

    /** Busca o roteiro ativo do motorista (planejando ou em rota) e, se em rota, os pontos de GPS. */
    fun carregar(motoristaUid: String) {
        this.motoristaUid = motoristaUid
        viewModelScope.launch {
            val roteiroAtivo = try {
                RoteiroRepositorio.buscarRoteiroAtivo(motoristaUid)
            } catch (erro: Exception) {
                null
            }
            if (roteiroAtivo == null) {
                _estado.value = RoteiroUiState(carregado = true, temRoteiro = false)
                return@launch
            }
            val (roteiroId, roteiro) = roteiroAtivo

            val viagensBrutas = try {
                ViagemRepositorio.buscarPorRoteiro(roteiroId)
            } catch (erro: Exception) {
                emptyList()
            }
            val viagens = viagensBrutas.map { (id, viagem) ->
                val pedido = try {
                    PedidoRepositorio.buscarPorId(viagem.pedidoId)
                } catch (erro: Exception) {
                    null
                }
                ViagemDoRoteiro(id, viagem, pedido?.destinatarioNome ?: "")
            }

            val emRota = roteiro.status == StatusRoteiro.EM_ROTA.name
            val pontos = if (emRota) {
                AppDatabase.obter(getApplication()).pontoGpsDao().buscarPorRoteiro(roteiroId)
            } else {
                emptyList()
            }

            val todasResolvidas = viagens.all { (_, viagem, _) ->
                viagem.status == StatusViagem.CONCLUIDA.name || viagem.status == StatusViagem.CANCELADA.name
            }

            _estado.value = RoteiroUiState(
                carregado = true,
                temRoteiro = true,
                roteiroId = roteiroId,
                status = roteiro.status,
                viagens = viagens,
                pontos = pontos,
                podeIniciar = roteiro.status == StatusRoteiro.PLANEJANDO.name && viagens.isNotEmpty(),
                podeEncerrar = emRota && todasResolvidas
            )
        }
    }

    /** Não espera confirmação do servidor — mesma escrita simples usada em todo o app motorista. */
    fun iniciarRoteiro() {
        val estado = _estado.value ?: return
        val roteiroId = estado.roteiroId ?: return
        RoteiroRepositorio.iniciarRoteiro(roteiroId, estado.viagens.map { it.viagemId })
        RastreamentoService.iniciar(getApplication(), roteiroId)
        carregar(motoristaUid)
    }

    /** Não espera confirmação do servidor — mesmo raciocínio de sempre. */
    fun marcarEntregue(viagemId: String) {
        RoteiroRepositorio.marcarEntregue(viagemId)
        carregar(motoristaUid)
    }

    /**
     * Calcula o km total (Haversine sobre os pontos do roteiro), busca a distância dedicada de
     * cada pedido envolvido, grava o rateio e encerra o roteiro. Só deve ser chamado quando
     * [RoteiroUiState.podeEncerrar] for true (todas as viagens já resolvidas).
     */
    fun encerrarRoteiro() {
        val roteiroId = _estado.value?.roteiroId ?: return
        viewModelScope.launch {
            try {
                val viagens = ViagemRepositorio.buscarPorRoteiro(roteiroId)
                val pedidoIds = viagens.map { (_, viagem) -> viagem.pedidoId }.distinct()
                val distancias = pedidoIds.associateWith { id ->
                    try {
                        PedidoRepositorio.buscarPorId(id)?.distanciaDedicadaKm ?: 0.0
                    } catch (erro: Exception) {
                        0.0
                    }
                }
                val km = PontoGpsRepositorio.calcularKmPercorridos(getApplication(), roteiroId)
                RoteiroRepositorio.encerrarRoteiro(roteiroId, km, viagens, distancias)
                RastreamentoService.encerrar(getApplication(), roteiroId)
                _estado.value = _estado.value?.copy(roteiroEncerrado = Evento(Unit))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(erro = Evento("Sem conexão. Tente encerrar de novo"))
            }
        }
    }
}
```

- [ ] **Step 2: `ViagemDoRoteiroAdapter`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemDoRoteiroAdapter.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagemDoRoteiro

/** Lista das viagens do roteiro atual, com um botão "marcar como entregue" por item pendente. */
class ViagemDoRoteiroAdapter(
    private val itens: List<ViagemDoRoteiro>,
    private val aoMarcarEntregue: (viagemId: String) -> Unit
) : RecyclerView.Adapter<ViagemDoRoteiroAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val botaoEntregue: Button = view.findViewById(R.id.botaoEntregue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_do_roteiro, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context
        holder.textDestinatario.text = item.destinatarioNome

        val pendente = item.viagem.status != StatusViagem.CONCLUIDA.name &&
            item.viagem.status != StatusViagem.CANCELADA.name
        holder.textStatus.text = contexto.getString(
            when (item.viagem.status) {
                StatusViagem.CONCLUIDA.name -> R.string.roteiro_item_entregue
                StatusViagem.CANCELADA.name -> R.string.roteiro_item_cancelada
                else -> R.string.roteiro_item_pendente
            }
        )
        holder.botaoEntregue.visibility = if (pendente) View.VISIBLE else View.GONE
        holder.botaoEntregue.setOnClickListener { aoMarcarEntregue(item.viagemId) }
    }

    override fun getItemCount(): Int = itens.size
}
```

- [ ] **Step 3: Layout de cada item da lista do roteiro**

Criar `app/src/main/res/layout/item_viagem_do_roteiro.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/textDestinatario"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        tools:text="Construtora Alvorada Ltda" />

    <TextView
        android:id="@+id/textStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        tools:text="Pendente" />

    <Button
        android:id="@+id/botaoEntregue"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/roteiro_botao_marcar_entregue" />

</LinearLayout>
```

- [ ] **Step 4: Layout da aba Roteiro — versão final**

Substituir o conteúdo completo de `app/src/main/res/layout/fragment_roteiro.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView
        android:id="@+id/textRoteiroVazio"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="24dp"
        android:gravity="center"
        android:text="@string/roteiro_vazio"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerViagensRoteiro"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:clipToPadding="false"
        android:padding="8dp"
        android:visibility="gone" />

    <Button
        android:id="@+id/botaoIniciarRoteiro"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:text="@string/roteiro_botao_iniciar"
        android:visibility="gone" />

    <FrameLayout
        android:id="@+id/containerMapa"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:visibility="gone">

        <fragment
            android:id="@+id/mapa"
            android:name="com.google.android.gms.maps.SupportMapFragment"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

    </FrameLayout>

    <Button
        android:id="@+id/botaoEncerrarRoteiro"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:text="@string/roteiro_botao_encerrar"
        android:visibility="gone" />

</LinearLayout>
```

- [ ] **Step 5: `RoteiroFragment` — implementação real**

Substituir o conteúdo completo de `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/RoteiroFragment.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions

/** Aba "Roteiro": roteiro atual do motorista — sem roteiro, planejando ou em rota. */
class RoteiroFragment : Fragment(R.layout.fragment_roteiro), OnMapReadyCallback {

    companion object {
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: RoteiroViewModel by viewModels()

    private lateinit var textRoteiroVazio: TextView
    private lateinit var recyclerViagensRoteiro: RecyclerView
    private lateinit var botaoIniciarRoteiro: Button
    private lateinit var containerMapa: View
    private lateinit var botaoEncerrarRoteiro: Button
    private var mapa: GoogleMap? = null
    private var estadoAtual: RoteiroUiState = RoteiroUiState()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textRoteiroVazio = view.findViewById(R.id.textRoteiroVazio)
        recyclerViagensRoteiro = view.findViewById(R.id.recyclerViagensRoteiro)
        recyclerViagensRoteiro.layoutManager = LinearLayoutManager(requireContext())
        botaoIniciarRoteiro = view.findViewById(R.id.botaoIniciarRoteiro)
        containerMapa = view.findViewById(R.id.containerMapa)
        botaoEncerrarRoteiro = view.findViewById(R.id.botaoEncerrarRoteiro)

        botaoIniciarRoteiro.setOnClickListener { confirmarIniciarRoteiro() }
        botaoEncerrarRoteiro.setOnClickListener { confirmarEncerrarRoteiro() }

        val fragmentoMapa = childFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }
    }

    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        desenharTrajeto(estadoAtual)
    }

    private fun exibirEstado(estado: RoteiroUiState) {
        estadoAtual = estado
        if (!estado.carregado) return

        if (!estado.temRoteiro) {
            textRoteiroVazio.visibility = View.VISIBLE
            recyclerViagensRoteiro.visibility = View.GONE
            botaoIniciarRoteiro.visibility = View.GONE
            containerMapa.visibility = View.GONE
            botaoEncerrarRoteiro.visibility = View.GONE
            return
        }

        textRoteiroVazio.visibility = View.GONE
        recyclerViagensRoteiro.visibility = View.VISIBLE
        recyclerViagensRoteiro.adapter = ViagemDoRoteiroAdapter(estado.viagens) { viagemId ->
            viewModel.marcarEntregue(viagemId)
        }

        val emRota = estado.status == StatusRoteiro.EM_ROTA.name
        botaoIniciarRoteiro.visibility = if (!emRota) View.VISIBLE else View.GONE
        botaoIniciarRoteiro.isEnabled = estado.podeIniciar
        containerMapa.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.isEnabled = estado.podeEncerrar

        desenharTrajeto(estado)

        estado.erro?.getConteudoSeNaoTratado()?.let { mensagem ->
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
        }
        estado.roteiroEncerrado?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(requireContext(), R.string.roteiro_encerrado_sucesso, Toast.LENGTH_SHORT).show()
        }
    }

    /** Desenha a Polyline do trajeto percorrido até agora (só faz sentido com o roteiro em rota). */
    private fun desenharTrajeto(estado: RoteiroUiState) {
        val mapaAtual = mapa ?: return
        if (estado.status != StatusRoteiro.EM_ROTA.name || estado.pontos.isEmpty()) return

        mapaAtual.clear()
        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }

    private fun confirmarIniciarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_iniciar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> viewModel.iniciarRoteiro() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    private fun confirmarEncerrarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_encerrar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> viewModel.encerrarRoteiro() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }
}
```

- [ ] **Step 6: Novas strings**

Em `app/src/main/res/values/strings.xml`, acrescentar:
```xml
    <string name="roteiro_botao_iniciar">Iniciar roteiro</string>
    <string name="roteiro_botao_encerrar">Encerrar roteiro</string>
    <string name="roteiro_botao_marcar_entregue">Marcar como entregue</string>
    <string name="roteiro_confirmar_iniciar">Iniciar o roteiro?</string>
    <string name="roteiro_confirmar_encerrar">Encerrar o roteiro? Só é possível se todas as entregas já estiverem resolvidas</string>
    <string name="roteiro_encerrado_sucesso">Roteiro encerrado</string>
    <string name="roteiro_item_pendente">Pendente</string>
    <string name="roteiro_item_entregue">Entregue</string>
    <string name="roteiro_item_cancelada">Cancelada</string>
```

- [ ] **Step 7: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Teste manual no celular — fluxo completo**

1. Adicionar 1-2 viagens ao roteiro (pela aba Viagens, botão "Adicionar ao roteiro") → abrir a aba Roteiro → confirmar que aparecem na lista, com "Iniciar roteiro" habilitado.
2. Tocar em "Iniciar roteiro" → confirmar no diálogo → a lista deve mostrar "Pendente" pra cada viagem, o mapa aparecer (vazio no início) e "Encerrar roteiro" desabilitado.
3. Dirigir um pouco (ou esperar alguns pontos de GPS) → confirmar que o trajeto (linha) aparece no mapa.
4. Tocar em "Marcar como entregue" numa das viagens (pela lista do roteiro OU pela tela de detalhe dela) → confirmar que o item muda pra "Entregue" e some o botão daquele item.
5. Com todas as viagens entregues, confirmar que "Encerrar roteiro" fica habilitado → tocar → confirmar no diálogo → voltar pra aba Roteiro mostrando "nenhum roteiro em andamento".
6. No histórico do pedido (app gerencial), confirmar que a viagem mostra o km atribuído.
7. **Testar cancelamento**: com uma entrega ainda pendente, cancelar a viagem remotamente pelo gerencial → confirmar que ela aparece como "Cancelada" na lista do roteiro (sem botão de entregar) → confirmar que "Encerrar roteiro" libera considerando ela resolvida.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/RoteiroFragment.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/RoteiroViewModel.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemDoRoteiroAdapter.kt app/src/main/res/layout/fragment_roteiro.xml app/src/main/res/layout/item_viagem_do_roteiro.xml app/src/main/res/values/strings.xml
git commit -m "Implementa a aba Roteiro: mapa do trajeto, lista de entregas, iniciar/marcar entregue/encerrar"
```

---

## Task 7: `NovoPedidoActivity` (gerencial) — campo `distanciaDedicadaKm`

**Contexto:** completa o que a Task 1 deixou pendente — o formulário de cadastro de pedido ganha o campo de distância (km, ida simples, base até o cliente), digitado manualmente pelo gerente. Sem essa task, `PedidoRepositorio.cadastrar` (já modificado na Task 1) não compila porque o único chamador dela não passa o parâmetro novo.

**Files:**
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/NovoPedidoActivity.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/NovoPedidoViewModel.kt`
- Modify: `gerencial/src/main/res/layout/activity_novo_pedido.xml`
- Modify: `gerencial/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PedidoRepositorio.cadastrar(..., distanciaDedicadaKm)` (Task 1).

- [ ] **Step 1: Layout — novo campo**

Em `gerencial/src/main/res/layout/activity_novo_pedido.xml`, acrescentar depois do `editQuantidadeM3` (antes do `botaoDataEntrega`):
```xml
        <EditText
            android:id="@+id/editDistanciaKm"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:hint="@string/novo_pedido_hint_distancia_km"
            android:inputType="numberDecimal" />
```

- [ ] **Step 2: `NovoPedidoViewModel` — valida e passa o campo novo**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/NovoPedidoViewModel.kt`, a `data class NovoPedidoUiState` ganha:
```kotlin
data class NovoPedidoUiState(
    val carregando: Boolean = false,
    val erroDocumento: Boolean = false,
    val erroNome: Boolean = false,
    val erroEndereco: Boolean = false,
    val erroQuantidade: Boolean = false,
    val erroDistanciaKm: Boolean = false,
    val erroDataEntrega: Boolean = false,
    val erroSalvar: Boolean = false,
    val sucesso: Evento<Int>? = null
)
```

E o método `salvar` inteiro vira:
```kotlin
    fun salvar(
        documentoDigitado: String,
        nome: String,
        cepDigitado: String,
        rua: String,
        numero: String,
        bairro: String,
        cidade: String,
        ufDigitada: String,
        quantidadeDigitada: String,
        distanciaDigitada: String,
        dataEntrega: Long?
    ) {
        val documento = DocumentoUtils.apenasDigitos(documentoDigitado)
        val tipoPessoa = DocumentoUtils.tipoPessoa(documento)
        val documentoInvalido = tipoPessoa == null || !DocumentoUtils.documentoValido(documento)
        val nomeInvalido = nome.isEmpty()

        val cep = DocumentoUtils.apenasDigitos(cepDigitado)
        val uf = ufDigitada.trim().uppercase()
        val enderecoInvalido = cep.length != 8 || rua.isEmpty() || numero.isEmpty() ||
            bairro.isEmpty() || cidade.isEmpty() || uf !in Uf.siglas

        val quantidadeM3 = quantidadeDigitada.trim().replace(',', '.').toDoubleOrNull()
        val quantidadeInvalida = quantidadeM3 == null || quantidadeM3 <= 0.0

        val distanciaKm = distanciaDigitada.trim().replace(',', '.').toDoubleOrNull()
        val distanciaInvalida = distanciaKm == null || distanciaKm <= 0.0

        val dataEntregaInvalida = dataEntrega == null

        if (documentoInvalido || nomeInvalido || enderecoInvalido || quantidadeInvalida ||
            distanciaInvalida || dataEntregaInvalida
        ) {
            _estado.value = _estado.value?.copy(
                erroDocumento = documentoInvalido,
                erroNome = nomeInvalido,
                erroEndereco = enderecoInvalido,
                erroQuantidade = quantidadeInvalida,
                erroDistanciaKm = distanciaInvalida,
                erroDataEntrega = dataEntregaInvalida,
                erroSalvar = false
            )
            return
        }

        _estado.value = _estado.value?.copy(
            carregando = true,
            erroDocumento = false,
            erroNome = false,
            erroEndereco = false,
            erroQuantidade = false,
            erroDistanciaKm = false,
            erroDataEntrega = false,
            erroSalvar = false
        )
        viewModelScope.launch {
            try {
                val id = PedidoRepositorio.cadastrar(
                    destinatarioNome = nome,
                    documento = documento,
                    tipoPessoa = requireNotNull(tipoPessoa),
                    rua = rua,
                    numero = numero,
                    bairro = bairro,
                    cidade = cidade,
                    uf = uf,
                    cep = cep,
                    quantidadeTotalM3 = requireNotNull(quantidadeM3),
                    dataEntrega = requireNotNull(dataEntrega),
                    distanciaDedicadaKm = requireNotNull(distanciaKm)
                )
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(id))
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroSalvar = true)
            }
        }
    }
```

- [ ] **Step 3: `NovoPedidoActivity` — liga o campo novo**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/NovoPedidoActivity.kt`, acrescentar o campo (junto aos outros `lateinit var`):
```kotlin
    private lateinit var editDistanciaKm: EditText
```

No `onCreate`, depois de `editQuantidadeM3 = findViewById(R.id.editQuantidadeM3)`:
```kotlin
        editDistanciaKm = findViewById(R.id.editDistanciaKm)
```

No `botaoSalvar.setOnClickListener`, a chamada a `viewModel.salvar` ganha o parâmetro:
```kotlin
        botaoSalvar.setOnClickListener {
            viewModel.salvar(
                documentoDigitado = editDocumento.text.toString(),
                nome = editNome.text.toString().trim(),
                cepDigitado = editCep.text.toString(),
                rua = editRua.text.toString().trim(),
                numero = editNumero.text.toString().trim(),
                bairro = editBairro.text.toString().trim(),
                cidade = editCidade.text.toString().trim(),
                ufDigitada = editUf.text.toString(),
                quantidadeDigitada = editQuantidadeM3.text.toString(),
                distanciaDigitada = editDistanciaKm.text.toString(),
                dataEntrega = dataEntregaSelecionada
            )
        }
```

Em `exibirEstado`, acrescentar a linha do erro novo (junto às outras `.error = ...`):
```kotlin
        editDistanciaKm.error = if (estado.erroDistanciaKm) getString(R.string.novo_pedido_erro_distancia_km) else null
```

- [ ] **Step 4: Novas strings**

Em `gerencial/src/main/res/values/strings.xml`, acrescentar perto de `novo_pedido_hint_m3`:
```xml
    <string name="novo_pedido_hint_distancia_km">Distância da usina até o cliente (km, ida)</string>
    <string name="novo_pedido_erro_distancia_km">Informe uma distância maior que zero</string>
```

- [ ] **Step 5: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Teste manual no celular**

Cadastrar um pedido novo sem preencher a distância → confirmar que aparece o erro no campo. Preencher com um valor válido → confirmar que salva normalmente.

- [ ] **Step 7: Commit**

```bash
git add gerencial/src/main/res/layout/activity_novo_pedido.xml gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/NovoPedidoViewModel.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/NovoPedidoActivity.kt gerencial/src/main/res/values/strings.xml
git commit -m "Cadastro de pedido ganha o campo de distância dedicada (km, ida simples)"
```

---

## Task 8: `RoteiroRepositorio` (gerencial)

**Contexto:** o gerencial só lê roteiros (criar/editar é sempre feito pelo motorista) — busca por id, escuta os em rota (pro mapa ao vivo) e lista os pontos de GPS (pro mapa do roteiro). Nada ainda chama essas funções (Tasks 9 e 10 consomem); compila sozinha.

**Files:**
- Create: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/RoteiroRepositorio.kt`

**Interfaces:**
- Consumes: `RoteiroRemota`, `StatusRoteiro`, `PontoGpsRemoto` (já existe) — Task 1.
- Produces: `RoteiroRepositorio.buscarPorId(roteiroId): RoteiroRemota?`, `.escutarRoteirosEmRota(aoAtualizar): ListenerRegistration`, `.listarPontosGps(roteiroId): List<PontoGpsRemoto>`.

- [ ] **Step 1: `RoteiroRepositorio`**

Criar `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/RoteiroRepositorio.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusRoteiro
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/** Consulta de roteiros — o gerencial só lê (criar/editar roteiro é sempre feito pelo motorista). */
object RoteiroRepositorio {

    private val db get() = Firebase.firestore

    /** Busca um roteiro específico pelo id do documento — usado na tela de detalhe do roteiro. */
    suspend fun buscarPorId(roteiroId: String): RoteiroRemota? {
        val documento = db.collection("roteiros").document(roteiroId).get().aguardar()
        return documento.toObject(RoteiroRemota::class.java)
    }

    /**
     * Escuta em tempo real os roteiros em rota agora — usado pelo mapa do gerente. Quem cancela
     * a escuta é o chamador, guardando o [ListenerRegistration] retornado e chamando `.remove()`
     * quando não precisar mais.
     */
    fun escutarRoteirosEmRota(aoAtualizar: (List<Pair<String, RoteiroRemota>>) -> Unit): ListenerRegistration {
        return db.collection("roteiros")
            .whereEqualTo("status", StatusRoteiro.EM_ROTA.name)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null || snapshot == null) return@addSnapshotListener
                val roteiros = snapshot.documents.mapNotNull { documento ->
                    val roteiro = documento.toObject(RoteiroRemota::class.java) ?: return@mapNotNull null
                    documento.id to roteiro
                }
                aoAtualizar(roteiros)
            }
    }

    /** Pontos de GPS gravados durante o roteiro, em ordem, pra desenhar o trajeto no mapa. */
    suspend fun listarPontosGps(roteiroId: String): List<PontoGpsRemoto> {
        val snapshot = db.collection("roteiros").document(roteiroId)
            .collection("pontosGps")
            .get()
            .aguardar()
        return snapshot.documents
            .mapNotNull { it.toObject(PontoGpsRemoto::class.java) }
            .sortedBy { it.horario }
    }
}
```

- [ ] **Step 2: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/RoteiroRepositorio.kt
git commit -m "Adiciona RoteiroRepositorio (gerencial): consulta de roteiros e seus pontos de GPS"
```

---

## Task 9: `MapaFragment`/`MapaViewModel` (gerencial) — caminhões por roteiro, não por viagem

**Contexto:** o mapa em tempo real hoje escuta viagens `EM_ROTA` e suas `ultimaLatitude/ultimaLongitude`. Como a posição agora é atualizada no documento do ROTEIRO (Task 4), o mapa passa a escutar roteiros `EM_ROTA`.

**Files:**
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/MapaViewModel.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/MapaFragment.kt`

**Interfaces:**
- Consumes: `RoteiroRepositorio.escutarRoteirosEmRota` (Task 8).

- [ ] **Step 1: `MapaViewModel` — troca de viagens pra roteiros**

Substituir o conteúdo completo de `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/MapaViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.RoteiroRepositorio
import com.google.firebase.firestore.ListenerRegistration

/** Um caminhão com roteiro em rota agora — só os dados, sem nada de mapa/Marker aqui. */
data class CaminhaoEmRota(
    val roteiroId: String,
    val latitude: Double,
    val longitude: Double,
    val motoristaNome: String
)

/** Estado exibido por [MapaFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.MapaFragment]. */
data class MapaUiState(val caminhoes: List<CaminhaoEmRota> = emptyList())

/**
 * Escuta [RoteiroRepositorio.escutarRoteirosEmRota] e expõe a lista de caminhões atualizados.
 * Desenhar isso no mapa (criar/mover Marker, animar câmera) é trabalho da View — um `Marker` só
 * existe junto com o `GoogleMap`, então não faz sentido a ViewModel guardar isso.
 */
class MapaViewModel : ViewModel() {

    private val _estado = MutableLiveData(MapaUiState())
    val estado: LiveData<MapaUiState> = _estado

    private var registroListener: ListenerRegistration? = null

    /** Começa a escutar os roteiros em rota; chamadas repetidas (ex.: giro de tela) não recriam o listener. */
    fun escutar() {
        if (registroListener != null) return

        registroListener = RoteiroRepositorio.escutarRoteirosEmRota { roteiros ->
            val caminhoes = roteiros.mapNotNull { (roteiroId, roteiro) ->
                val lat = roteiro.ultimaLatitude ?: return@mapNotNull null
                val lng = roteiro.ultimaLongitude ?: return@mapNotNull null
                CaminhaoEmRota(
                    roteiroId = roteiroId,
                    latitude = lat,
                    longitude = lng,
                    motoristaNome = roteiro.motoristaNome
                )
            }
            _estado.value = MapaUiState(caminhoes)
        }
    }

    /** ViewModel sendo destruída de vez (não é só giro de tela): encerra o listener do Firestore. */
    override fun onCleared() {
        super.onCleared()
        registroListener?.remove()
    }
}
```

- [ ] **Step 2: `MapaFragment` — usa `roteiroId` em vez de `viagemId`**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/MapaFragment.kt`, dentro de `exibirCaminhoes`, trocar toda referência a `caminhao.viagemId` por `caminhao.roteiroId` (usado como chave do `Map` de marcadores e no cálculo de `idsAtivos`). O restante do método (posição, título, criação/atualização/remoção de `Marker`) permanece igual — o `titulo` usa `R.string.mapa_marcador_formato` com `motoristaNome`/`caminhaoPlaca`, mas `CaminhaoEmRota` não tem mais `caminhaoPlaca` (o roteiro não guarda essa informação — ver Task 1). Trocar a montagem do título para usar só o nome do motorista:
```kotlin
            val titulo = caminhao.motoristaNome
```
(Remove o uso de `R.string.mapa_marcador_formato` neste ponto — a string continua existindo em `strings.xml` mas fica sem uso aqui; deixar como está, não precisa apagá-la, é só um recurso não referenciado nesta tela específica.)

- [ ] **Step 3: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Teste manual no celular**

Com um roteiro em rota no app motorista, abrir a aba Mapa do gerencial → confirmar que aparece um marcador com o nome do motorista, se movendo conforme os pontos de GPS chegam.

- [ ] **Step 5: Commit**

```bash
git add gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/MapaViewModel.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/MapaFragment.kt
git commit -m "Mapa em tempo real do gerencial passa a mostrar roteiros em rota, não viagens"
```

---

## Task 10: `DetalhesRoteiroActivity` (gerencial) — substitui `RotaViagemActivity`

**Contexto:** a última task do plano. Como os pontos de GPS agora pertencem ao roteiro (não a uma viagem isolada), a tela de "rota de uma viagem" deixa de fazer sentido sozinha — vira a tela do roteiro inteiro: mapa com o trajeto completo + uma tabela com cada pedido do roteiro (volume, distância dedicada, peso teórico, % do rateio, km atribuído), destacando a viagem que originou a navegação (se veio de um clique específico). `HistoricoPedidoActivity`/`ViagemAdapter` passam a navegar pra cá usando o `roteiroId` da viagem, só quando ela tiver um (viagens que nunca entraram num roteiro não têm essa navegação disponível).

**Files:**
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRepositorio.kt`
- Create: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/DetalhesRoteiroViewModel.kt`
- Create: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/DetalhesRoteiroActivity.kt`
- Create: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ItemRateioAdapter.kt`
- Create: `gerencial/src/main/res/layout/activity_detalhes_roteiro.xml`
- Create: `gerencial/src/main/res/layout/item_rateio.xml`
- Delete: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/RotaViagemActivity.kt`
- Delete: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/RotaViagemViewModel.kt`
- Delete: `gerencial/src/main/res/layout/activity_rota_viagem.xml`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/HistoricoPedidoActivity.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ViagemAdapter.kt`
- Modify: `gerencial/src/main/AndroidManifest.xml`
- Modify: `gerencial/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RoteiroRepositorio.buscarPorId/listarPontosGps` (Task 8).
- Produces: `ViagemRepositorio.listarPorRoteiro(roteiroId): List<Pair<String, ViagemRemota>>`.

- [ ] **Step 1: `ViagemRepositorio` — nova consulta, remove a antiga (agora morta)**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRepositorio.kt`, substituir o método `listarPontosGps` (que lia `viagens/{id}/pontosGps`, agora sem uso — os pontos são lidos via `RoteiroRepositorio.listarPontosGps`) por:
```kotlin
    /** Viagens de um roteiro específico — usado na tela de detalhe do roteiro (mapa + rateio). */
    suspend fun listarPorRoteiro(roteiroId: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("roteiroId", roteiroId)
            .get()
            .aguardar()
        return snapshot.documents
            .mapNotNull { documento ->
                val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
                documento.id to remota
            }
            .sortedBy { it.second.dataAtribuicao }
    }
```
(O import de `PontoGpsRemoto` no topo do arquivo fica sem uso depois dessa troca — remover esse import também. O resto do arquivo — `atribuir`, `cancelar`, `buscarPorId`, `listarPorPedido`, `escutarViagensEmRota` — continua igual.)

- [ ] **Step 2: `DetalhesRoteiroViewModel`**

Criar `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/DetalhesRoteiroViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PontoGpsRemoto
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.RoteiroRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusViagem
import kotlinx.coroutines.launch

/** Uma linha da tabela de rateio: um pedido do roteiro, com peso/percentual/km já calculados. */
data class ItemRateio(
    val viagemId: String,
    val pedidoId: Int,
    val destinatarioNome: String,
    val volumeM3: Double,
    val distanciaDedicadaKm: Double,
    val percentualRateio: Double,
    val kmAtribuido: Double?,
    val destacado: Boolean
)

/** Estado exibido por [DetalhesRoteiroActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.DetalhesRoteiroActivity]. */
data class DetalhesRoteiroUiState(
    val carregado: Boolean = false,
    val pontos: List<PontoGpsRemoto> = emptyList(),
    val motoristaNome: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmTotalRota: Double? = null,
    val itens: List<ItemRateio> = emptyList()
)

/**
 * Carrega o roteiro, seus pontos de GPS e a tabela de rateio (peso teórico e percentual
 * recalculados aqui pra exibição; o `kmAtribuido` real de cada viagem já foi gravado por
 * `RoteiroRepositorio.encerrarRoteiro`, no app motorista, ao encerrar — aqui só se lê).
 */
class DetalhesRoteiroViewModel : ViewModel() {

    private val _estado = MutableLiveData(DetalhesRoteiroUiState())
    val estado: LiveData<DetalhesRoteiroUiState> = _estado

    fun carregar(roteiroId: String, viagemDestacadaId: String?) {
        viewModelScope.launch {
            val roteiro = try {
                RoteiroRepositorio.buscarPorId(roteiroId)
            } catch (erro: Exception) {
                null
            } ?: return@launch

            val pontos = try {
                RoteiroRepositorio.listarPontosGps(roteiroId)
            } catch (erro: Exception) {
                emptyList()
            }

            val viagens = try {
                ViagemRepositorio.listarPorRoteiro(roteiroId)
            } catch (erro: Exception) {
                emptyList()
            }

            val distanciasPorPedido = viagens.map { (_, viagem) -> viagem.pedidoId }.distinct()
                .associateWith { id ->
                    try {
                        PedidoRepositorio.buscarPorId(id)?.distanciaDedicadaKm ?: 0.0
                    } catch (erro: Exception) {
                        0.0
                    }
                }

            val pesoTotal = viagens.sumOf { (_, viagem) ->
                if (viagem.status == StatusViagem.CANCELADA.name) {
                    0.0
                } else {
                    viagem.metrosCubicos * (distanciasPorPedido[viagem.pedidoId] ?: 0.0)
                }
            }

            val itens = viagens.map { (id, viagem) ->
                val pedido = try {
                    PedidoRepositorio.buscarPorId(viagem.pedidoId)
                } catch (erro: Exception) {
                    null
                }
                val distancia = distanciasPorPedido[viagem.pedidoId] ?: 0.0
                val pesoTeorico = viagem.metrosCubicos * distancia
                val percentual = if (pesoTotal > 0.0 && viagem.status != StatusViagem.CANCELADA.name) {
                    pesoTeorico / pesoTotal * 100.0
                } else {
                    0.0
                }
                ItemRateio(
                    viagemId = id,
                    pedidoId = viagem.pedidoId,
                    destinatarioNome = pedido?.destinatarioNome ?: "",
                    volumeM3 = viagem.metrosCubicos,
                    distanciaDedicadaKm = distancia,
                    percentualRateio = percentual,
                    kmAtribuido = viagem.kmPercorridos,
                    destacado = id == viagemDestacadaId
                )
            }

            _estado.value = DetalhesRoteiroUiState(
                carregado = true,
                pontos = pontos,
                motoristaNome = roteiro.motoristaNome,
                status = roteiro.status,
                dataInicio = roteiro.dataInicio,
                dataFim = roteiro.dataFim,
                kmTotalRota = roteiro.kmTotalRota,
                itens = itens
            )
        }
    }
}
```

- [ ] **Step 3: `ItemRateioAdapter`**

Criar `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ItemRateioAdapter.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ItemRateio
import java.util.Locale

/** Uma linha da tabela de rateio do roteiro: pedido, peso e km atribuído. */
class ItemRateioAdapter(
    private val itens: List<ItemRateio>
) : RecyclerView.Adapter<ItemRateioAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPedido: TextView = view.findViewById(R.id.textPedido)
        val textPeso: TextView = view.findViewById(R.id.textPeso)
        val textRateio: TextView = view.findViewById(R.id.textRateio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rateio, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context

        holder.textPedido.text = contexto.getString(
            R.string.detalhes_roteiro_item_formato, item.pedidoId, item.destinatarioNome
        )
        holder.textPeso.text = contexto.getString(
            R.string.detalhes_roteiro_item_peso_formato,
            formatarM3(item.volumeM3),
            formatarKm(item.distanciaDedicadaKm)
        )
        holder.textRateio.text = if (item.kmAtribuido != null) {
            val percentual = String.format(Locale.getDefault(), "%.1f", item.percentualRateio)
            contexto.getString(
                R.string.detalhes_roteiro_item_rateio_formato, percentual, formatarKm(item.kmAtribuido)
            )
        } else {
            contexto.getString(R.string.detalhes_roteiro_item_sem_km)
        }

        holder.itemView.setBackgroundColor(
            if (item.destacado) Color.parseColor("#FFF3CD") else Color.TRANSPARENT
        )
    }

    override fun getItemCount(): Int = itens.size
}
```

- [ ] **Step 4: Layout do item da tabela de rateio**

Criar `gerencial/src/main/res/layout/item_rateio.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/textPedido"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        tools:text="Pedido nº 4 — Construtora Alvorada Ltda" />

    <TextView
        android:id="@+id/textPeso"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        tools:text="6 m³ × 12,0 km = peso 72,0" />

    <TextView
        android:id="@+id/textRateio"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textStyle="italic"
        tools:text="35,2% do roteiro · 18,4 km atribuídos" />

</LinearLayout>
```

- [ ] **Step 5: Layout da tela**

Criar `gerencial/src/main/res/layout/activity_detalhes_roteiro.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="#E6FFFFFF"
        android:padding="12dp">

        <TextView
            android:id="@+id/textMotorista"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textStyle="bold"
            tools:text="Motorista: Carlos" />

        <TextView
            android:id="@+id/textPeriodo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            tools:text="Início 20/09/2026 14:30 · Fim 20/09/2026 16:10" />

        <TextView
            android:id="@+id/textKmTotal"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textStyle="bold"
            android:visibility="gone"
            tools:text="42,0 km percorridos (total do roteiro)" />

    </LinearLayout>

    <TextView
        android:id="@+id/textSemPontos"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="12dp"
        android:gravity="center"
        android:text="@string/rota_sem_pontos"
        android:visibility="gone" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <fragment
            android:id="@+id/mapa"
            android:name="com.google.android.gms.maps.SupportMapFragment"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

    </FrameLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerRateio"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="280dp"
        android:clipToPadding="false"
        android:padding="8dp" />

</LinearLayout>
```

- [ ] **Step 6: `DetalhesRoteiroActivity`**

Criar `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/DetalhesRoteiroActivity.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.DetalhesRoteiroUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.DetalhesRoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Detalhe de um roteiro: mapa com o trajeto inteiro e a tabela de rateio de km por pedido. */
class DetalhesRoteiroActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        /** Chave do Intent extra: id do roteiro a ser mostrado. */
        const val EXTRA_ROTEIRO_ID = "roteiro_id"
        /** Chave do Intent extra, opcional: viagem a destacar na tabela (quando veio de um clique específico). */
        const val EXTRA_VIAGEM_DESTACADA_ID = "viagem_destacada_id"
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: DetalhesRoteiroViewModel by viewModels()

    private var mapa: GoogleMap? = null
    private lateinit var roteiroId: String
    private lateinit var textSemPontos: TextView
    private lateinit var textMotorista: TextView
    private lateinit var textPeriodo: TextView
    private lateinit var textKmTotal: TextView
    private lateinit var recyclerRateio: RecyclerView
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_roteiro)
        title = getString(R.string.detalhes_roteiro_titulo)

        val idRecebido = intent.getStringExtra(EXTRA_ROTEIRO_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        roteiroId = idRecebido
        val viagemDestacadaId = intent.getStringExtra(EXTRA_VIAGEM_DESTACADA_ID)

        textSemPontos = findViewById(R.id.textSemPontos)
        textMotorista = findViewById(R.id.textMotorista)
        textPeriodo = findViewById(R.id.textPeriodo)
        textKmTotal = findViewById(R.id.textKmTotal)
        recyclerRateio = findViewById(R.id.recyclerRateio)
        recyclerRateio.layoutManager = LinearLayoutManager(this)

        val fragmentoMapa = supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(roteiroId, viagemDestacadaId)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        viewModel.estado.value?.let { exibirEstado(it) }
    }

    private fun exibirEstado(estado: DetalhesRoteiroUiState) {
        if (!estado.carregado) return

        textMotorista.text = getString(R.string.detalhes_roteiro_motorista_formato, estado.motoristaNome)

        val inicio = estado.dataInicio?.let { formatoData.format(Date(it)) } ?: ""
        val fim = estado.dataFim
        textPeriodo.text = if (fim != null) {
            getString(R.string.detalhes_roteiro_periodo_formato, inicio, formatoData.format(Date(fim)))
        } else {
            getString(R.string.detalhes_roteiro_inicio_formato, inicio)
        }

        if (estado.kmTotalRota != null) {
            textKmTotal.text = getString(R.string.detalhes_roteiro_km_total_formato, formatarKm(estado.kmTotalRota))
            textKmTotal.visibility = View.VISIBLE
        } else {
            textKmTotal.visibility = View.GONE
        }

        recyclerRateio.adapter = ItemRateioAdapter(estado.itens)

        val mapaAtual = mapa ?: return

        if (estado.pontos.isEmpty()) {
            textSemPontos.visibility = View.VISIBLE
            return
        }
        textSemPontos.visibility = View.GONE

        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        mapaAtual.addMarker(
            MarkerOptions().position(posicoes.first())
                .title(getString(R.string.rota_marcador_inicio))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        if (posicoes.size > 1) {
            mapaAtual.addMarker(
                MarkerOptions().position(posicoes.last())
                    .title(getString(R.string.rota_marcador_fim))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }
}
```
Apagar `RotaViagemActivity.kt`, `RotaViagemViewModel.kt` e `activity_rota_viagem.xml`.

- [ ] **Step 7: `HistoricoPedidoActivity` — navega por roteiro**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/HistoricoPedidoActivity.kt`, dentro de `exibirEstado`, o `ViagemAdapter(...)` construído troca o callback `aoVerRota`:
```kotlin
            recyclerViagens.adapter = ViagemAdapter(
                estado.viagens,
                aoCancelar = { viagemId, viagem -> confirmarCancelamento(viagemId, viagem) },
                aoVerRota = { viagemId, roteiroId ->
                    val intent = Intent(this, DetalhesRoteiroActivity::class.java)
                        .putExtra(DetalhesRoteiroActivity.EXTRA_ROTEIRO_ID, roteiroId)
                        .putExtra(DetalhesRoteiroActivity.EXTRA_VIAGEM_DESTACADA_ID, viagemId)
                    startActivity(intent)
                }
            )
```
(Só essa chamada muda — o resto do arquivo continua igual.)

- [ ] **Step 8: `ViagemAdapter` — clicável só com `roteiroId`, passa o id do roteiro**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ViagemAdapter.kt`, a assinatura da classe e o fim de `onBindViewHolder` mudam:
```kotlin
class ViagemAdapter(
    private val itens: List<Pair<String, ViagemRemota>>,
    private val aoCancelar: (viagemId: String, viagem: ViagemRemota) -> Unit,
    private val aoVerRota: (viagemId: String, roteiroId: String) -> Unit
) : RecyclerView.Adapter<ViagemAdapter.ViewHolder>() {
```
E, no fim de `onBindViewHolder` (onde hoje está `temRota`/`isClickable`/`setOnClickListener`):
```kotlin
        val roteiroId = item.roteiroId
        holder.itemView.isClickable = roteiroId != null
        holder.itemView.setOnClickListener {
            if (roteiroId != null) aoVerRota(viagemId, roteiroId)
        }
```
(Resto do método — `textMotorista`, `textStatus`, `textDetalhes`, `textKm`, `botaoCancelar` — sem mudanças.)

- [ ] **Step 9: Manifest**

Em `gerencial/src/main/AndroidManifest.xml`, trocar:
```xml
        <activity
            android:name=".ui.RotaViagemActivity"
            android:exported="false" />
```
por:
```xml
        <activity
            android:name=".ui.DetalhesRoteiroActivity"
            android:exported="false" />
```

- [ ] **Step 10: Strings — novas e remoção das órfãs**

Em `gerencial/src/main/res/values/strings.xml`, acrescentar perto de `rota_marcador_fim`:
```xml
    <string name="detalhes_roteiro_titulo">Detalhes do roteiro</string>
    <string name="detalhes_roteiro_motorista_formato">Motorista: %1$s</string>
    <string name="detalhes_roteiro_inicio_formato">Início %1$s</string>
    <string name="detalhes_roteiro_periodo_formato">Início %1$s · Fim %2$s</string>
    <string name="detalhes_roteiro_km_total_formato">%1$s km percorridos (total do roteiro)</string>
    <string name="detalhes_roteiro_item_formato">Pedido nº %1$d — %2$s</string>
    <string name="detalhes_roteiro_item_peso_formato">%1$s m³ × %2$s km</string>
    <string name="detalhes_roteiro_item_rateio_formato">%1$s%% do roteiro · %2$s km atribuídos</string>
    <string name="detalhes_roteiro_item_sem_km">Aguardando o roteiro ser encerrado</string>
```

Trocar o texto (mantendo o nome) de `rota_sem_pontos`:
```xml
    <string name="rota_sem_pontos">Nenhum ponto de GPS registrado neste roteiro</string>
```

Remover (superadas pela tela nova — confirmar com `grep` antes, igual à Task 5, já que `rota_titulo`/`rota_pedido_formato`/`rota_quantidade_formato`/`rota_periodo_formato`/`rota_inicio_formato`/`rota_km_formato` eram usadas só por `RotaViagemActivity`, que esta task apaga):
- `rota_titulo`
- `rota_pedido_formato`
- `rota_quantidade_formato`
- `rota_periodo_formato`
- `rota_inicio_formato`
- `rota_km_formato`

(`rota_marcador_inicio`, `rota_marcador_fim` continuam em uso pela `DetalhesRoteiroActivity` nova — não remover essas duas. `mapa_marcador_formato` fica sem uso desde a Task 9 — se o grep confirmar, remover também nesta task, já que é aqui que sobra evidente que ninguém mais vai usá-la.)

- [ ] **Step 11: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Teste manual no celular**

No histórico de um pedido cuja viagem já foi lançada num roteiro, tocar nela → confirmar que abre a tela do roteiro com o mapa (trajeto completo) e a tabela de rateio, com a linha daquela viagem destacada. Uma viagem que nunca entrou em roteiro (ainda `ATRIBUIDA` sem `roteiroId`) não deve reagir ao toque.

- [ ] **Step 13: Commit**

```bash
git add gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRepositorio.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/DetalhesRoteiroViewModel.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/DetalhesRoteiroActivity.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ItemRateioAdapter.kt gerencial/src/main/res/layout/activity_detalhes_roteiro.xml gerencial/src/main/res/layout/item_rateio.xml gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/HistoricoPedidoActivity.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ViagemAdapter.kt gerencial/src/main/AndroidManifest.xml gerencial/src/main/res/values/strings.xml
git commit -m "Substitui RotaViagemActivity por DetalhesRoteiroActivity: mapa do roteiro inteiro + tabela de rateio de km"
```

---

## Self-Review (já aplicado ao escrever o plano)

**Cobertura da spec:** todas as seções da spec (`2026-09-20-roteiro-multiplos-pedidos-design.md`) têm task correspondente — modelo de dados (Task 1), telas do motorista (Tasks 2, 5, 6), backend do motorista (Tasks 3, 4), app gerencial (Tasks 7, 8, 9, 10).

**Gaps encontrados e corrigidos durante a exploração do código pra escrever este plano** (não estavam na spec original, descobertos ao olhar o código existente):
- `Roteiro` precisa de `ultimaLatitude`/`ultimaLongitude`/`ultimoHorario` (Task 1) — sem isso o mapa em tempo real do gerencial (Task 9) não teria de onde ler a posição ao vivo.
- `Roteiro` precisa de `motoristaNome` denormalizado (Task 1) — mesmo padrão já usado em `ViagemRemota`, necessário pro marcador do mapa mostrar um nome em vez de só o uid.
- O mapa em tempo real do gerencial (`MapaFragment`/`MapaViewModel`) e a tela de rota de uma viagem (`RotaViagemActivity`) dependiam de dados por-viagem que deixam de existir — viraram as Tasks 9 e 10.
- `RoteiroRemota` não tem campo de caminhão próprio (evita conflito com o caminhão já escolhido por viagem na atribuição) — validado na hora de adicionar (Task 3).

**Sem placeholders:** todo step tem código completo, sem "TODO"/"implementar depois" (exceto a Task 2's `RoteiroFragment`, que é deliberadamente um placeholder até a Task 6 — documentado explicitamente como tal, não é um placeholder esquecido).

**Consistência de tipos:** `RastreamentoService.iniciar/encerrar(context, roteiroId)` usado igual nas Tasks 4 e 6; `PontoGpsRepositorio.calcularKmPercorridos(context, roteiroId): Double` usado igual nas Tasks 4 e 6; `RoteiroRepositorio.encerrarRoteiro(roteiroId, kmTotalRota, viagens, distanciasPorPedido)` com a mesma assinatura nas Tasks 3 e 6 (única chamadora).

**Grafo de dependências (verificado acíclico):** 1 → {2, 3, 7, 8}; 2 → {4}; 3 → {5, 6}; 4 → {6}; 8 → {9, 10}. Diferente do plano anterior, nenhuma task depende de uma classe que só é criada por uma task posterior — a ordem 1..10 builda e commita cada task isoladamente, sem "compile unit" nem ruling de build adiado.
