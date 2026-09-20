# Fluxo de viagens do motorista, GPS multi-viagem e km percorrido — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir o bug de rastreamento GPS com viagens simultâneas, consolidar o fluxo de telas do app motorista numa lista única + tela de detalhe, e calcular/exibir km percorrido por viagem e por pedido nos dois apps.

**Architecture:** MVVM em toda parte (Activity/Fragment → ViewModel com LiveData → Repository `object` → Room/Firestore), seguindo os padrões já estabelecidos no projeto. `RastreamentoService` passa de "uma viagem" para "um conjunto de viagens ativas". Km é calculado sob demanda (Haversine sobre os pontos do Room) e nunca gravado como total agregado no pedido — só por viagem, e somado na hora de exibir (mesmo padrão já usado pelo status do pedido).

**Tech Stack:** Kotlin, Android Views (XML), Room, Firebase Auth/Firestore, Google Maps SDK (`play-services-maps` — **novo no módulo `app`**, já existe no `gerencial`), FusedLocationProviderClient (`play-services-location`, já existe no `app`).

**Spec:** `docs/superpowers/specs/2026-09-20-fluxo-viagens-motorista-e-km-design.md`

## Global Constraints

- Comentários e mensagens de commit em português (CLAUDE.md).
- Sem framework de testes automatizado configurado no projeto — cada task termina com `gradlew.bat assembleDebug` (build limpo) em vez de testes unitários, e uma verificação manual no celular físico é descrita ao final de cada task (GPS/serviço em segundo plano não são confiáveis em emulador).
- Não commitar sem o usuário pedir — este plano descreve os commits como parte natural de cada task, mas quem executa deve confirmar com o usuário antes do primeiro commit real, conforme o restante da sessão já vem fazendo.
- **Task 1 adiciona uma dependência nova** (`play-services-maps` ao módulo `app`) — avisar o usuário explicitamente antes de aplicar essa task (regra do CLAUDE.md: não adicionar dependência sem avisar/explicar).
- Nomes de arquivo/classe seguem o padrão já usado no projeto (Activity/ViewModel/layout com o mesmo nome-base).
- Toda escrita no Firestore que hoje é "sem esperar confirmação do servidor" (iniciar trânsito, finalizar viagem) continua assim — nenhuma task deste plano introduz transação nem espera de rede nesses pontos.

---

## Task 1: Google Maps no módulo `app` (motorista)

**Contexto:** a nova `DetalhesViagemActivity` (Task 6) precisa de um mapa, mas o módulo `app` (motorista) nunca teve o SDK do Google Maps — só o `gerencial` tem. Esta task só liga a dependência; nenhuma tela nova ainda.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/google_maps_api_key.xml`

**Interfaces:**
- Produces: dependência `libs.play.services.maps` disponível para o módulo `app`; meta-data `com.google.android.geo.API_KEY` no Manifest do `app`, lendo `@string/google_maps_key`.

- [ ] **Step 1: Confirmar que `playServicesMaps`/`play-services-maps` já existem no catálogo**

`gradle/libs.versions.toml` já tem (usado pelo `gerencial`):
```toml
playServicesMaps = "20.0.0"
...
play-services-maps = { group = "com.google.android.gms", name = "play-services-maps", version.ref = "playServicesMaps" }
```
Não precisa adicionar nada no catálogo — só usar no `app/build.gradle.kts`.

- [ ] **Step 2: Adicionar a dependência ao módulo `app`**

Em `app/build.gradle.kts`, dentro do bloco `dependencies { ... }`, logo abaixo de `implementation(libs.play.services.location)`:
```kotlin
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
```

- [ ] **Step 3: Copiar a chave da API de mapas do `gerencial` para o `app`**

O `gerencial` já tem `gerencial/src/main/res/values/google_maps_api_key.xml` com a chave real (é o mesmo projeto Firebase/Google Cloud — `entregas-concreto`). Crie o arquivo equivalente no `app`:

`app/src/main/res/values/google_maps_api_key.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
      Chave da API do Google Maps — mesma do módulo gerencial (mesmo projeto no Google Cloud).
      Se o mapa aparecer em branco/cinza neste app, a chave pode estar restrita por nome de
      pacote no Google Cloud Console só para br.com.anderson.meuprimeiroprojeto.gerencial —
      nesse caso, adicione br.com.anderson.meuprimeiroprojeto às restrições da chave lá.
    -->
    <string name="google_maps_key" translatable="false">AIzaSyBvlgG0BmqOMxptONH55cfevIr-PTFWJ6M</string>
</resources>
```

- [ ] **Step 4: Declarar a meta-data da API key no Manifest do `app`**

Em `app/src/main/AndroidManifest.xml`, dentro de `<application>`, depois do `<service>` do `RastreamentoService` e antes do fechamento de `</application>`:
```xml
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="@string/google_maps_key" />
```

- [ ] **Step 5: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` nos dois módulos (essa task ainda não usa o mapa em lugar nenhum, só disponibiliza).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/values/google_maps_api_key.xml
git commit -m "Adiciona Google Maps SDK ao app motorista (dependência nova, para a tela de detalhes da viagem)"
```

---

## Task 2: Campo `kmPercorridos` e `formatarKm`

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRemota.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRemota.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/FormatoM3.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/FormatoM3.kt`

**Interfaces:**
- Produces: `ViagemRemota.kmPercorridos: Double?` (motorista e gerencial); `formatarKm(valor: Double): String` (motorista e gerencial).

- [ ] **Step 1: Adicionar o campo em `ViagemRemota` (motorista)**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRemota.kt`, o campo final da data class vira:
```kotlin
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val ultimoHorario: Long? = null,
    val kmPercorridos: Double? = null
)
```

- [ ] **Step 2: Adicionar o campo em `ViagemRemota` (gerencial)**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRemota.kt`:
```kotlin
    val ultimaLatitude: Double? = null,
    val ultimaLongitude: Double? = null,
    val kmPercorridos: Double? = null
)
```

- [ ] **Step 3: Adicionar `formatarKm` (motorista)**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/FormatoM3.kt`, acrescentar depois de `formatarM3`:
```kotlin
/** Formata km com uma casa decimal (12.0 -> "12,0", 12.34 -> "12,3"). */
fun formatarKm(valor: Double): String {
    return String.format(Locale.getDefault(), "%.1f", valor)
}
```

- [ ] **Step 4: Adicionar `formatarKm` (gerencial)**

Mesma função, em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/FormatoM3.kt`.

- [ ] **Step 5: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (campo novo com default `null`/função nova não usada ainda não quebra nada).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRemota.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRemota.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/FormatoM3.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/FormatoM3.kt
git commit -m "Adiciona campo kmPercorridos ao modelo de viagem e formatarKm nos dois apps"
```

---

## Task 3: Cálculo de km (Haversine) — `PontoGpsRepositorio`

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsRepositorio.kt`

**Interfaces:**
- Consumes: `AppDatabase.obter(context).pontoGpsDao().buscarPorViagem(viagemId): List<PontoGps>` (já existe).
- Produces: `suspend fun PontoGpsRepositorio.calcularKmPercorridos(context: Context, viagemId: String): Double`.

- [ ] **Step 1: Escrever a função de distância e o cálculo do total**

Conteúdo completo de `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsRepositorio.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.db

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Contagem de pontos de GPS gravados localmente (fila offline) e cálculo do km percorrido. */
object PontoGpsRepositorio {

    private const val RAIO_TERRA_METROS = 6_371_000.0

    suspend fun contarTotalEPendentes(context: Context, viagemId: String): Pair<Int, Int> {
        val dao = AppDatabase.obter(context).pontoGpsDao()
        val total = dao.contarPorViagem(viagemId)
        val pendentes = dao.contarPendentesPorViagem(viagemId)
        return total to pendentes
    }

    /**
     * Soma a distância (fórmula de Haversine) entre cada par de pontos consecutivos da viagem,
     * usando os pontos já salvos localmente (Room) — não depende de terem sincronizado com o
     * Firestore. Recalculado sob demanda: com um ponto a cada 10s, mesmo uma viagem de horas
     * tem no máximo algumas centenas de pontos, então somar tudo de novo é instantâneo.
     */
    suspend fun calcularKmPercorridos(context: Context, viagemId: String): Double {
        val pontos = AppDatabase.obter(context).pontoGpsDao().buscarPorViagem(viagemId)
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

- [ ] **Step 2: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verificação manual da fórmula (sem framework de teste)**

Não há JUnit configurado no projeto para testar isolado. Verificação rápida de sanidade: duas coordenadas ~1km afastadas na latitude (ex.: `(-27.05083, -49.62278)` e `(-27.06, -49.62278)`, diferença de ~0.009° de latitude ≈ 1000m) devem dar `calcularKmPercorridos` próximo de 1.0 km para uma lista de 2 pontos com essas coordenadas. Isso será confirmado na prática na Task 6 (tela de detalhes), dirigindo uma distância conhecida.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/db/PontoGpsRepositorio.kt
git commit -m "Adiciona cálculo de km percorrido (Haversine) ao PontoGpsRepositorio"
```

---

## Task 4: `RastreamentoService` com múltiplas viagens

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/gps/RastreamentoService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (referência à Activity de destino da notificação, ajustada na Task 5 — nesta task a notificação só perde o texto específico de uma viagem).

**Interfaces:**
- Produces: `RastreamentoService.iniciar(context: Context, viagemId: String)`, `RastreamentoService.encerrar(context: Context, viagemId: String)` — chamados pela `DetalhesViagemActivity` (Task 6) no lugar do `Intent`/`startForegroundService`/`stopService` manuais de hoje.

- [ ] **Step 1: Reescrever o serviço**

Conteúdo completo de `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/gps/RastreamentoService.kt`:
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
import br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagensAbertasActivity
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
 * Serviço em primeiro plano que grava pontos de GPS de uma ou mais viagens em rota ao mesmo
 * tempo — [viagensAtivas] guarda o conjunto de viagens sendo rastreadas agora. O GPS só é
 * ligado quando a primeira viagem entra no conjunto, e só é desligado quando o conjunto fica
 * vazio; entre isso, cada posição recebida grava um ponto para TODAS as viagens ativas.
 */
class RastreamentoService : Service() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
        private const val ACTION_INICIAR = "br.com.anderson.meuprimeiroprojeto.INICIAR_VIAGEM"
        private const val ACTION_ENCERRAR = "br.com.anderson.meuprimeiroprojeto.ENCERRAR_VIAGEM"
        private const val CANAL_ID = "rastreamento_viagem"
        private const val NOTIFICACAO_ID = 1001
        private const val INTERVALO_ATUALIZACAO_MS = 10_000L
        private const val TAG = "RastreamentoService"

        /** Passa a rastrear esta viagem também; se já houver outra ativa, o GPS já está ligado. */
        fun iniciar(context: Context, viagemId: String) {
            val intent = Intent(context, RastreamentoService::class.java)
                .setAction(ACTION_INICIAR)
                .putExtra(EXTRA_VIAGEM_ID, viagemId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Para de rastrear esta viagem; o serviço só encerra de vez se não sobrar nenhuma outra ativa. */
        fun encerrar(context: Context, viagemId: String) {
            val intent = Intent(context, RastreamentoService::class.java)
                .setAction(ACTION_ENCERRAR)
                .putExtra(EXTRA_VIAGEM_ID, viagemId)
            context.startService(intent)
        }
    }

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clienteLocalizacao: FusedLocationProviderClient
    private var callbackLocalizacao: LocationCallback? = null
    private val viagensAtivas = mutableSetOf<String>()

    /** Prepara o cliente de localização (FusedLocationProviderClient) quando o serviço é criado. */
    override fun onCreate() {
        super.onCreate()
        clienteLocalizacao = LocationServices.getFusedLocationProviderClient(this)
    }

    /**
     * Adiciona ou remove uma viagem do conjunto de viagens ativas, conforme a `action` do Intent.
     * START_STICKY: se o sistema matar o processo por falta de memória, o Android tenta recriar
     * o serviço (o Intent original pode vir nulo nesse caso — perde o conjunto em memória, mesmo
     * comportamento que o serviço já tinha antes desta mudança).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val viagemId = intent?.getStringExtra(EXTRA_VIAGEM_ID)
        if (viagemId.isNullOrBlank()) {
            if (viagensAtivas.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_ENCERRAR) {
            viagensAtivas.remove(viagemId)
            if (viagensAtivas.isEmpty()) {
                callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val jaTinhaViagemAtiva = viagensAtivas.isNotEmpty()
        viagensAtivas.add(viagemId)
        criarCanalDeNotificacao()
        startForeground(NOTIFICACAO_ID, criarNotificacao(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (!jaTinhaViagemAtiva) {
            iniciarAtualizacoesDeLocalizacao()
        }
        return START_STICKY
    }

    /**
     * Registra o callback do FusedLocationProviderClient para receber posições a cada
     * [INTERVALO_ATUALIZACAO_MS]; cada posição recebida é gravada no Room para todas as
     * viagens em [viagensAtivas] no momento, e depois dispara a sincronização da fila offline.
     */
    private fun iniciarAtualizacoesDeLocalizacao() {
        val temPermissao = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!temPermissao) {
            stopSelf()
            return
        }

        val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_ATUALIZACAO_MS).build()
        val callback = object : LocationCallback() {
            /** Grava a posição recebida no Room e atualiza o Firestore, para cada viagem ativa no momento. */
            override fun onLocationResult(resultado: LocationResult) {
                val localizacao = resultado.lastLocation ?: return
                val horario = System.currentTimeMillis()
                val idsAtivosAgora = viagensAtivas.toSet()
                escopo.launch {
                    for (idViagem in idsAtivosAgora) {
                        AppDatabase.obter(applicationContext).pontoGpsDao().inserir(
                            PontoGps(
                                viagemId = idViagem,
                                latitude = localizacao.latitude,
                                longitude = localizacao.longitude,
                                horario = horario
                            )
                        )
                        atualizarPosicaoAtual(idViagem, localizacao.latitude, localizacao.longitude, horario)
                    }
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
                Firebase.firestore.collection("viagens").document(ponto.viagemId)
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
                Log.w(TAG, "Falha ao sincronizar ponto ${ponto.id} da viagem ${ponto.viagemId}", erro)
            }
        }
    }

    /**
     * Atualiza a posição atual no próprio documento da viagem — é o que o mapa em tempo real
     * do app gerencial escuta. Separado da fila offline de propósito: se falhar por falta de
     * conexão não tem problema guardar pra depois, a próxima atualização (10s) já traz uma
     * posição mais nova mesmo.
     */
    private suspend fun atualizarPosicaoAtual(viagemId: String, latitude: Double, longitude: Double, horario: Long) {
        try {
            Firebase.firestore.collection("viagens").document(viagemId)
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

    private fun criarCanalDeNotificacao() {
        val gerenciador = getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(
            CANAL_ID, getString(R.string.notificacao_rastreamento_canal), NotificationManager.IMPORTANCE_LOW
        )
        gerenciador.createNotificationChannel(canal)
    }

    private fun criarNotificacao(): Notification {
        val intentAbrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, ViagensAbertasActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(getString(R.string.notificacao_rastreamento_titulo))
            .setContentText(getString(R.string.notificacao_rastreamento_texto))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(intentAbrirApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }
        escopo.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

Note: essa versão já referencia `ViagensAbertasActivity` (ainda não existe — criada na Task 5). O projeto não compila entre a Task 4 e a Task 5 isoladamente; **por isso a Task 4 e a Task 5 devem ser implementadas e buildadas juntas antes do primeiro commit desta dupla** (o Step de build desta task já reflete isso).

- [ ] **Step 2: Build (só depois de também aplicar a Task 5, por causa da referência a `ViagensAbertasActivity`)**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (depois de Task 5 aplicada).

- [ ] **Step 3: Commit (junto com a Task 5, já que uma depende da outra pra compilar)**

O commit desta task fica combinado com o commit da Task 5 — ver o Step de commit lá.

---

## Task 5: `ViagensAbertasActivity` (substitui `MinhasViagensActivity`)

**Files:**
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/ViagemAbertaComPedido.kt`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/ViagemAtribuidaComPedido.kt`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/ViagensAbertasViewModel.kt`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/MinhasViagensViewModel.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensAbertasActivity.kt`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/MinhasViagensActivity.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemAbertaAdapter.kt`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemAtribuidaAdapter.kt`
- Create: `app/src/main/res/layout/item_viagem_aberta.xml`
- Delete: `app/src/main/res/layout/item_viagem_atribuida.xml`
- Create: `app/src/main/res/layout/activity_viagens_abertas.xml`
- Delete: `app/src/main/res/layout/activity_minhas_viagens.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/LoginActivity.kt` (destino após login)

**Interfaces:**
- Consumes: `RastreamentoService` (Task 4, referenciado só pela notificação — sem chamada direta aqui).
- Produces: `ViagensAbertasActivity` como launcher pós-login; ao tocar num item, abre `DetalhesViagemActivity` (Task 6) com `EXTRA_VIAGEM_ID`.

- [ ] **Step 1: Renomear `ViagemAtribuidaComPedido` para `ViagemAbertaComPedido`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/ViagemAbertaComPedido.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.dados

import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota

/** Uma viagem em aberto (atribuída ou em rota) deste motorista, já com os dados do pedido. */
data class ViagemAbertaComPedido(
    val viagemId: String,
    val viagem: ViagemRemota,
    val pedido: Pedido?
)
```
Apagar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/ViagemAtribuidaComPedido.kt`.

- [ ] **Step 2: Trocar as duas buscas por uma única `buscarAbertasDoMotorista`**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt`, substituir o método `buscarAtribuidasDoMotorista` por:
```kotlin
    /** Viagens em aberto deste motorista: atribuídas (não iniciadas) ou em rota — a lista única da tela principal. */
    suspend fun buscarAbertasDoMotorista(motoristaUid: String): List<Pair<String, ViagemRemota>> {
        val snapshot = db.collection("viagens")
            .whereEqualTo("motoristaUid", motoristaUid)
            .whereIn("status", listOf(StatusViagem.ATRIBUIDA.name, StatusViagem.EM_ROTA.name))
            .get()
            .aguardar()
        return snapshot.documents.mapNotNull { documento ->
            val remota = documento.toObject(ViagemRemota::class.java) ?: return@mapNotNull null
            documento.id to remota
        }.sortedBy { it.second.dataAtribuicao }
    }
```
E remover o método `buscarEmRotaDoMotorista` inteiro (não é mais usado — a lista única já cobre viagens em rota).

- [ ] **Step 3: Nova ViewModel `ViagensAbertasViewModel`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/ViagensAbertasViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAbertaComPedido
import kotlinx.coroutines.launch

/** Estado exibido por [ViagensAbertasActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagensAbertasActivity]. */
data class ViagensAbertasUiState(
    val viagens: List<ViagemAbertaComPedido> = emptyList(),
    val erroAoCarregar: Boolean = false
)

/**
 * Busca as viagens em aberto do motorista (atribuídas ou em rota) e junta cada uma com os
 * dados do seu pedido. A Activity só observa [estado] e desenha a lista.
 */
class ViagensAbertasViewModel : ViewModel() {

    private val _estado = MutableLiveData(ViagensAbertasUiState())
    val estado: LiveData<ViagensAbertasUiState> = _estado

    fun carregar(motoristaUid: String) {
        viewModelScope.launch {
            try {
                val abertas = ViagemRepositorio.buscarAbertasDoMotorista(motoristaUid)
                val comPedido = abertas.map { (viagemId, viagem) ->
                    val pedido = try {
                        PedidoRepositorio.buscarPorId(viagem.pedidoId)
                    } catch (erro: Exception) {
                        null
                    }
                    ViagemAbertaComPedido(viagemId, viagem, pedido)
                }
                _estado.value = ViagensAbertasUiState(viagens = comPedido, erroAoCarregar = false)
            } catch (erro: Exception) {
                _estado.value = ViagensAbertasUiState(erroAoCarregar = true)
            }
        }
    }
}
```
Apagar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/MinhasViagensViewModel.kt`.

- [ ] **Step 4: Novo item de lista — `item_viagem_aberta.xml`**

Criar `app/src/main/res/layout/item_viagem_aberta.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="?attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true"
    android:padding="16dp">

    <TextView
        android:id="@+id/textDestinatario"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textStyle="bold"
        tools:text="Construtora Alvorada Ltda" />

    <TextView
        android:id="@+id/textEndereco"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        tools:text="Rua das Palmeiras, 120 - Centro, Sorocaba/SP" />

    <TextView
        android:id="@+id/textDetalhes"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textStyle="italic"
        tools:text="6 m³ · caminhão ABC1D23" />

    <TextView
        android:id="@+id/textStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textStyle="bold"
        tools:text="Em rota" />

</LinearLayout>
```
Apagar `app/src/main/res/layout/item_viagem_atribuida.xml`.

- [ ] **Step 5: Novo adapter `ViagemAbertaAdapter`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemAbertaAdapter.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAbertaComPedido
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3

/**
 * Adapter do RecyclerView com as viagens em aberto do motorista (atribuídas ou em rota) na
 * [ViagensAbertasActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagensAbertasActivity].
 * Não guarda estado próprio: recebe a lista pronta e um callback de clique, e só desenha.
 * CPF/CNPJ do destinatário não aparece aqui — só nome, endereço e m³ (regra de LGPD do projeto).
 */
class ViagemAbertaAdapter(
    private val itens: List<ViagemAbertaComPedido>,
    private val aoClicar: (ViagemAbertaComPedido) -> Unit
) : RecyclerView.Adapter<ViagemAbertaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textEndereco: TextView = view.findViewById(R.id.textEndereco)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_aberta, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context
        holder.textDestinatario.text = item.pedido?.destinatarioNome ?: ""
        holder.textEndereco.text = item.pedido?.enderecoResumido() ?: ""
        holder.textDetalhes.text = contexto.getString(
            R.string.viagens_item_detalhes_formato, formatarM3(item.viagem.metrosCubicos), item.viagem.caminhaoPlaca
        )
        holder.textStatus.text = contexto.getString(
            if (item.viagem.status == StatusViagem.EM_ROTA.name) {
                R.string.viagens_status_em_rota
            } else {
                R.string.viagens_status_atribuida
            }
        )
        holder.itemView.setOnClickListener { aoClicar(item) }
    }

    override fun getItemCount(): Int = itens.size
}
```
Apagar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagemAtribuidaAdapter.kt`.

- [ ] **Step 6: Novo layout `activity_viagens_abertas.xml`**

Criar `app/src/main/res/layout/activity_viagens_abertas.xml` (igual ao `activity_minhas_viagens.xml` de hoje, só sem o `botaoViagemEmAndamento` — a lista única já cobre isso):
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
Apagar `app/src/main/res/layout/activity_minhas_viagens.xml`.

- [ ] **Step 7: Nova Activity `ViagensAbertasActivity`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ViagensAbertasActivity.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasViewModel

/** Lista única das viagens em aberto do motorista (atribuídas ou em rota). */
class ViagensAbertasActivity : AppCompatActivity() {

    private val viewModel: ViagensAbertasViewModel by viewModels()

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viagens_abertas)

        findViewById<TextView>(R.id.textBoasVindas).text =
            getString(R.string.viagens_boas_vindas, SessaoMotorista.nomeMotorista(this))

        findViewById<Button>(R.id.botaoSair).setOnClickListener {
            AutenticacaoRepositorio.sair()
            SessaoMotorista.sair(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        recyclerViagens = findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(this)
        textListaVazia = findViewById(R.id.textListaVazia)

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Recarrega a lista sempre que a tela volta a ficar visível (ex.: após voltar da tela de detalhes). */
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
                val intent = Intent(this, DetalhesViagemActivity::class.java)
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
Apagar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/MinhasViagensActivity.kt`.

Nota: essa Activity referencia `DetalhesViagemActivity` (Task 6, ainda não existe). Igual à Task 4, **a Task 5 só compila depois da Task 6 também estar aplicada.**

- [ ] **Step 8: Atualizar `LoginActivity` para abrir `ViagensAbertasActivity`**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/LoginActivity.kt`, o método `abrirMinhasViagens` vira:
```kotlin
    private fun abrirListaDeViagens() {
        startActivity(Intent(this, ViagensAbertasActivity::class.java))
        finish()
    }
```
E a chamada em `exibirEstado`: `estado.sucesso?.getConteudoSeNaoTratado()?.let { abrirListaDeViagens() }`.

- [ ] **Step 9: Atualizar o Manifest**

Em `app/src/main/AndroidManifest.xml`, trocar:
```xml
        <activity
            android:name=".motorista.ui.MinhasViagensActivity"
            android:exported="false" />
```
por:
```xml
        <activity
            android:name=".motorista.ui.ViagensAbertasActivity"
            android:exported="false" />
        <activity
            android:name=".motorista.ui.DetalhesViagemActivity"
            android:exported="false" />
```
(remover também as entradas de `IniciarTransitoActivity` e `ViagemEmAndamentoActivity` — feito na Task 6, já que são apagadas lá).

- [ ] **Step 10: Novas strings**

Em `app/src/main/res/values/strings.xml`, acrescentar:
```xml
    <string name="viagens_status_atribuida">Atribuída — toque para ver detalhes</string>
    <string name="viagens_status_em_rota">Em rota — toque para ver detalhes</string>
```

- [ ] **Step 11: Build (só compila com a Task 6 também aplicada)**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Teste manual no celular**

Fazer login como motorista → confirmar que abre a lista de viagens em aberto (não mais a tela antiga) → se houver viagens atribuídas e/ou em rota, confirmar que todas aparecem juntas na lista, cada uma com o status certo → tocar numa viagem e confirmar que abre a tela de detalhes (Task 6).

- [ ] **Step 13: Commit (Task 4 + Task 5 juntas)**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/gps/RastreamentoService.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/dados/ app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ app/src/main/res/layout/item_viagem_aberta.xml app/src/main/res/layout/activity_viagens_abertas.xml app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "Rastreamento GPS passa a suportar várias viagens ao mesmo tempo; lista única de viagens em aberto"
```

---

## Task 6: `DetalhesViagemActivity` (substitui `IniciarTransitoActivity` + `ViagemEmAndamentoActivity`)

**Files:**
- Modify: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/DetalhesViagemViewModel.kt`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/IniciarTransitoViewModel.kt`, `ViagemEmAndamentoViewModel.kt`
- Create: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/DetalhesViagemActivity.kt`
- Delete: `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/IniciarTransitoActivity.kt`, `ViagemEmAndamentoActivity.kt`
- Create: `app/src/main/res/layout/activity_detalhes_viagem.xml`
- Delete: `app/src/main/res/layout/activity_iniciar_transito.xml`, `activity_viagem_em_andamento.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RastreamentoService.iniciar(context, viagemId)` / `.encerrar(context, viagemId)` (Task 4); `PontoGpsRepositorio.calcularKmPercorridos(context, viagemId)` (Task 3); `PontoGpsDao.buscarPorViagem` (já existe, para desenhar a Polyline).
- Produces: `DetalhesViagemActivity.EXTRA_VIAGEM_ID` — usado por `ViagensAbertasActivity` (Task 5).

- [ ] **Step 1: `ViagemRepositorio` — `finalizarViagem` ganha o km, e novo `gravarKmPercorridos`**

Em `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt`, substituir o método `finalizarViagem` por:
```kotlin
    /** Mesmo raciocínio de [iniciarTransito]: finalizar não pode depender de ter sinal agora. */
    fun finalizarViagem(viagemId: String, kmPercorridos: Double) {
        db.collection("viagens").document(viagemId)
            .update(
                mapOf(
                    "status" to StatusViagem.CONCLUIDA.name,
                    "dataFim" to System.currentTimeMillis(),
                    "kmPercorridos" to kmPercorridos
                )
            )
    }

    /**
     * Grava só o km percorrido, sem mexer em status/data — usado quando o gerente cancela a
     * viagem remotamente (o status e a data de cancelamento já foram gravados por ele).
     */
    fun gravarKmPercorridos(viagemId: String, kmPercorridos: Double) {
        db.collection("viagens").document(viagemId).update("kmPercorridos", kmPercorridos)
    }
```

- [ ] **Step 2: Nova ViewModel `DetalhesViagemViewModel`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/viewmodel/DetalhesViagemViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGpsRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/** Estado exibido por [DetalhesViagemActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.DetalhesViagemActivity]. */
data class DetalhesViagemUiState(
    val carregado: Boolean = false,
    val erroAoCarregar: Boolean = false,
    val destinatarioNome: String = "",
    val enderecoResumido: String = "",
    val metrosCubicos: Double = 0.0,
    val caminhaoPlaca: String = "",
    val status: String = "",
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double = 0.0,
    val pontos: List<PontoGps> = emptyList(),
    val cancelamento: Evento<String>? = null,
    val encerrada: Evento<Unit>? = null
)

/**
 * Carrega os dados da viagem/pedido; se a viagem estiver em rota, também carrega os pontos de
 * GPS (pra desenhar o trajeto) e o km já percorrido, e escuta cancelamento remoto do gerente.
 * É [AndroidViewModel] porque precisa do Room (pontos de GPS, cálculo de km).
 */
class DetalhesViagemViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(DetalhesViagemUiState())
    val estado: LiveData<DetalhesViagemUiState> = _estado

    private var registroCancelamento: ListenerRegistration? = null
    private var jaTratouEncerramento = false

    /** Busca a viagem/pedido; se em rota, também os pontos de GPS e o km, e liga a escuta de cancelamento. */
    fun carregar(viagemId: String) {
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

            val emRota = viagem.status == StatusViagem.EM_ROTA.name
            val pontos = if (emRota) {
                AppDatabase.obter(getApplication()).pontoGpsDao().buscarPorViagem(viagemId)
            } else {
                emptyList()
            }
            val km = if (emRota) PontoGpsRepositorio.calcularKmPercorridos(getApplication(), viagemId) else 0.0

            _estado.value = DetalhesViagemUiState(
                carregado = true,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                enderecoResumido = pedido?.enderecoResumido() ?: "",
                metrosCubicos = viagem.metrosCubicos,
                caminhaoPlaca = viagem.caminhaoPlaca,
                status = viagem.status,
                dataInicio = viagem.dataInicio,
                dataFim = viagem.dataFim,
                kmPercorridos = km,
                pontos = pontos
            )

            if (emRota) escutarCancelamento(viagemId)
        }
    }

    /** Não espera confirmação do servidor — funciona sem sinal (mesmo raciocínio de sempre). */
    fun confirmarInicio(viagemId: String) {
        ViagemRepositorio.iniciarTransito(viagemId)
        carregar(viagemId)
    }

    /** Calcula o km final a partir dos pontos locais e grava junto com status/dataFim, numa escrita só. */
    fun confirmarEncerramento(viagemId: String) {
        jaTratouEncerramento = true
        viewModelScope.launch {
            val km = PontoGpsRepositorio.calcularKmPercorridos(getApplication(), viagemId)
            ViagemRepositorio.finalizarViagem(viagemId, km)
            _estado.value = _estado.value?.copy(encerrada = Evento(Unit))
        }
    }

    /**
     * O gerente pode cancelar a viagem pelo app dele enquanto o motorista está rodando. Ao
     * detectar, calcula e grava o km já percorrido (o caminhão rodou aquela distância de
     * verdade), mesmo a viagem ficando cancelada.
     */
    private fun escutarCancelamento(viagemId: String) {
        if (registroCancelamento != null) return
        registroCancelamento = ViagemRepositorio.escutarViagem(viagemId) { viagem ->
            if (!jaTratouEncerramento && viagem.status == StatusViagem.CANCELADA.name) {
                jaTratouEncerramento = true
                viewModelScope.launch {
                    val km = PontoGpsRepositorio.calcularKmPercorridos(getApplication(), viagemId)
                    ViagemRepositorio.gravarKmPercorridos(viagemId, km)
                }
                _estado.value = _estado.value?.copy(cancelamento = Evento(viagem.motivoCancelamento ?: ""))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        registroCancelamento?.remove()
    }
}
```

- [ ] **Step 3: Novo layout `activity_detalhes_viagem.xml`**

Criar `app/src/main/res/layout/activity_detalhes_viagem.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

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

        <TextView
            android:id="@+id/textAvisoPermissao"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:textColor="#B00020"
            android:visibility="gone" />

        <Button
            android:id="@+id/botaoAjustarBateria"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/andamento_bateria_botao"
            android:visibility="gone" />

        <TextView
            android:id="@+id/textBateria"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="@string/andamento_bateria_texto"
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

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <fragment
            android:id="@+id/mapa"
            android:name="com.google.android.gms.maps.SupportMapFragment"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <TextView
            android:id="@+id/textSemPontos"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="top|center_horizontal"
            android:layout_margin="16dp"
            android:background="#CCFFFFFF"
            android:padding="12dp"
            android:visibility="gone" />

    </FrameLayout>

</LinearLayout>
```
Apagar `activity_iniciar_transito.xml` e `activity_viagem_em_andamento.xml`.

- [ ] **Step 4: Nova Activity `DetalhesViagemActivity`**

Criar `app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/DetalhesViagemActivity.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.formatarKm
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela única de detalhe de uma viagem: mostra os dados do pedido e um mapa — em branco com a
 * posição atual se a viagem ainda não começou, ou com o trajeto todo se já está em rota — e um
 * botão que confirma "Iniciar" ou "Encerrar" conforme o status.
 */
class DetalhesViagemActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
        private val LOCAL_PADRAO = LatLng(-27.05083, -49.62278)
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: DetalhesViagemViewModel by viewModels()
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private val clienteLocalizacao: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private lateinit var viagemId: String
    private lateinit var textPeriodo: TextView
    private lateinit var textKm: TextView
    private lateinit var botaoAjustarBateria: Button
    private lateinit var textBateria: TextView
    private lateinit var botaoAcao: Button
    private lateinit var textErro: TextView
    private lateinit var textSemPontos: TextView
    private var mapa: GoogleMap? = null
    private var estadoAtual: DetalhesViagemUiState = DetalhesViagemUiState()

    private val launcherPermissoesLocalizacao =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultados ->
            val localizacaoConcedida = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (localizacaoConcedida) {
                mostrarPosicaoAtualSeNaoIniciada()
            } else {
                findViewById<TextView>(R.id.textAvisoPermissao).apply {
                    text = getString(R.string.andamento_permissao_negada)
                    visibility = View.VISIBLE
                }
            }
        }

    private val launcherPermissaoSegundoPlano =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            RastreamentoService.iniciar(this, viagemId)
        }

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
        botaoAjustarBateria = findViewById(R.id.botaoAjustarBateria)
        textBateria = findViewById(R.id.textBateria)
        botaoAcao = findViewById(R.id.botaoAcao)
        textErro = findViewById(R.id.textErro)
        textSemPontos = findViewById(R.id.textSemPontos)

        botaoAcao.setOnClickListener { confirmarAcao() }

        val fragmentoMapa = supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        pedirPermissaoDeLocalizacao()

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(viagemId)
    }

    override fun onResume() {
        super.onResume()
        verificarOtimizacaoDeBateria()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        mapa?.moveCamera(CameraUpdateFactory.newLatLngZoom(LOCAL_PADRAO, 12f))
        desenharMapa(estadoAtual)
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

        val emRota = estado.status == StatusViagem.EM_ROTA.name
        if (emRota) {
            val inicio = estado.dataInicio?.let { formatoData.format(Date(it)) } ?: ""
            textPeriodo.text = getString(R.string.detalhes_viagem_inicio_formato, inicio)
            textPeriodo.visibility = View.VISIBLE
            textKm.text = getString(R.string.detalhes_viagem_km_formato, formatarKm(estado.kmPercorridos))
            textKm.visibility = View.VISIBLE
            botaoAcao.text = getString(R.string.detalhes_viagem_botao_encerrar)
            verificarOtimizacaoDeBateria()
        } else {
            textPeriodo.visibility = View.GONE
            textKm.visibility = View.GONE
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
            botaoAcao.text = getString(R.string.iniciar_transito_botao)
        }

        desenharMapa(estado)

        estado.cancelamento?.getConteudoSeNaoTratado()?.let { motivo -> tratarCancelamentoRemoto(motivo) }
        estado.encerrada?.getConteudoSeNaoTratado()?.let { tratarEncerramentoConfirmado() }
    }

    /** Desenha o trajeto (viagem em rota) ou a posição atual (viagem ainda não iniciada). */
    private fun desenharMapa(estado: DetalhesViagemUiState) {
        val mapaAtual = mapa ?: return
        if (!estado.carregado) return

        if (estado.status == StatusViagem.EM_ROTA.name) {
            textSemPontos.visibility = if (estado.pontos.isEmpty()) View.VISIBLE else View.GONE
            if (estado.pontos.isEmpty()) return

            mapaAtual.clear()
            val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
            mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
            if (posicoes.size == 1) {
                mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
            } else {
                val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
                mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
            }
        } else {
            textSemPontos.visibility = View.GONE
            mostrarPosicaoAtualSeNaoIniciada()
        }
    }

    /** Marca a posição atual no mapa (leitura pontual, sem ligar o rastreamento em segundo plano). */
    private fun mostrarPosicaoAtualSeNaoIniciada() {
        val mapaAtual = mapa ?: return
        if (estadoAtual.status == StatusViagem.EM_ROTA.name) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        clienteLocalizacao.lastLocation.addOnSuccessListener { localizacao ->
            if (localizacao == null) return@addOnSuccessListener
            val posicao = LatLng(localizacao.latitude, localizacao.longitude)
            mapaAtual.clear()
            mapaAtual.addMarker(MarkerOptions().position(posicao).title(getString(R.string.detalhes_viagem_posicao_atual)))
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicao, 15f))
        }
    }

    private fun confirmarAcao() {
        val emRota = estadoAtual.status == StatusViagem.EM_ROTA.name
        val tituloDialogo = if (emRota) {
            R.string.detalhes_viagem_confirmar_encerrar
        } else {
            R.string.detalhes_viagem_confirmar_iniciar
        }
        AlertDialog.Builder(this)
            .setTitle(tituloDialogo)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ ->
                if (emRota) {
                    RastreamentoService.encerrar(this, viagemId)
                    viewModel.confirmarEncerramento(viagemId)
                } else {
                    viewModel.confirmarInicio(viagemId)
                    pedirPermissaoDeSegundoPlanoEIniciarServico()
                }
            }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    private fun tratarEncerramentoConfirmado() {
        Toast.makeText(this, R.string.andamento_viagem_finalizada, Toast.LENGTH_SHORT).show()
        val intent = Intent(this, ViagensAbertasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun tratarCancelamentoRemoto(motivo: String) {
        RastreamentoService.encerrar(this, viagemId)
        Toast.makeText(this, getString(R.string.andamento_viagem_cancelada, motivo), Toast.LENGTH_LONG).show()
        val intent = Intent(this, ViagensAbertasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun pedirPermissaoDeLocalizacao() {
        val jaTemLocalizacao = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (jaTemLocalizacao) return

        val permissoes = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissoes.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcherPermissoesLocalizacao.launch(permissoes.toTypedArray())
    }

    private fun pedirPermissaoDeSegundoPlanoEIniciarServico() {
        val jaTemSegundoPlano = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (jaTemSegundoPlano) {
            RastreamentoService.iniciar(this, viagemId)
        } else {
            launcherPermissaoSegundoPlano.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun verificarOtimizacaoDeBateria() {
        if (estadoAtual.status != StatusViagem.EM_ROTA.name) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val ignorandoOtimizacao = powerManager.isIgnoringBatteryOptimizations(packageName)
        botaoAjustarBateria.visibility = if (ignorandoOtimizacao) View.GONE else View.VISIBLE
        textBateria.visibility = botaoAjustarBateria.visibility
        botaoAjustarBateria.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
```
Apagar `IniciarTransitoActivity.kt` e `ViagemEmAndamentoActivity.kt`.

- [ ] **Step 5: Novas strings**

Em `app/src/main/res/values/strings.xml`, acrescentar:
```xml
    <string name="detalhes_viagem_inicio_formato">Iniciada em: %1$s</string>
    <string name="detalhes_viagem_km_formato">%1$s km percorridos</string>
    <string name="detalhes_viagem_posicao_atual">Sua posição atual</string>
    <string name="detalhes_viagem_confirmar_iniciar">Iniciar o trânsito?</string>
    <string name="detalhes_viagem_confirmar_encerrar">Finalizar a viagem?</string>
    <string name="detalhes_viagem_botao_encerrar">Encerrar</string>
```
(reaproveita `cancelar_viagem_confirmar`/`cancelar_viagem_voltar` — já existem no `strings.xml` do gerencial; **conferir se essas duas chaves já existem no `strings.xml` do `app` (motorista) — se não existirem, adicionar `<string name="cancelar_viagem_confirmar">Confirmar</string>` e `<string name="cancelar_viagem_voltar">Voltar</string>` também**).

- [ ] **Step 6: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (essa é a build que também valida as Tasks 4 e 5, já que todas as três se referenciam).

- [ ] **Step 7: Teste manual no celular — fluxo completo**

1. Abrir uma viagem `ATRIBUIDA` pela lista: confirmar que aparece o marcador da posição atual no mapa (pede permissão de localização se for a primeira vez) e o botão mostra "Iniciar trânsito".
2. Tocar em "Iniciar trânsito" → confirmar no diálogo → a tela deve recarregar mostrando "Encerrar", km percorridos (começa em 0,0) e "Iniciada em: ...".
3. Andar um pouco (ou esperar alguns pontos de GPS) e voltar pra tela (ou tocar em atualizar) → confirmar que o trajeto (linha) aparece no mapa e o km sobe.
4. **Testar duas viagens ao mesmo tempo**: iniciar uma segunda viagem (se houver outra atribuída) sem finalizar a primeira → confirmar que AMBAS continuam recebendo pontos de GPS (esse era o bug original).
5. Tocar em "Encerrar" → confirmar no diálogo → volta pra lista, a viagem finalizada não aparece mais nela.
6. No app gerencial, cancelar remotamente uma viagem em rota do motorista → confirmar que o app do motorista mostra o aviso e volta pra lista.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/backend/ViagemRepositorio.kt app/src/main/java/br/com/anderson/meuprimeiroprojeto/motorista/ui/ app/src/main/res/layout/activity_detalhes_viagem.xml app/src/main/res/values/strings.xml
git commit -m "Consolida IniciarTransitoActivity e ViagemEmAndamentoActivity em DetalhesViagemActivity, com mapa e km percorrido"
```

---

## Task 7: Painel de detalhes na `RotaViagemActivity` (gerencial)

**Files:**
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRepositorio.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/RotaViagemViewModel.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/RotaViagemActivity.kt`
- Modify: `gerencial/src/main/res/layout/activity_rota_viagem.xml`
- Modify: `gerencial/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `ViagemRepositorio.buscarPorId(viagemId): ViagemRemota?` (gerencial — hoje só existe no motorista).

- [ ] **Step 1: `buscarPorId` no `ViagemRepositorio` do gerencial**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRepositorio.kt`, acrescentar (perto de `listarPorPedido`):
```kotlin
    /** Busca uma viagem específica pelo id do documento — usado no painel de detalhes da tela de rota. */
    suspend fun buscarPorId(viagemId: String): ViagemRemota? {
        val documento = db.collection("viagens").document(viagemId).get().aguardar()
        return documento.toObject(ViagemRemota::class.java)
    }
```

- [ ] **Step 2: `RotaViagemViewModel` busca viagem + pedido também**

Substituir o conteúdo de `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/RotaViagemViewModel.kt`:
```kotlin
package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PontoGpsRemoto
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import kotlinx.coroutines.launch

/** Estado exibido por [RotaViagemActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.RotaViagemActivity]. */
data class RotaViagemUiState(
    val pontos: List<PontoGpsRemoto> = emptyList(),
    val carregado: Boolean = false,
    val pedidoId: Int = 0,
    val destinatarioNome: String = "",
    val metrosCubicos: Double = 0.0,
    val dataInicio: Long? = null,
    val dataFim: Long? = null,
    val kmPercorridos: Double? = null
)

/** Busca os pontos de GPS e os dados da viagem/pedido — desenhar a rota é trabalho da View. */
class RotaViagemViewModel : ViewModel() {

    private val _estado = MutableLiveData(RotaViagemUiState())
    val estado: LiveData<RotaViagemUiState> = _estado

    fun carregar(viagemId: String) {
        viewModelScope.launch {
            val pontos = try {
                ViagemRepositorio.listarPontosGps(viagemId)
            } catch (erro: Exception) {
                emptyList()
            }

            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                null
            }
            val pedido = viagem?.let {
                try {
                    PedidoRepositorio.buscarPorId(it.pedidoId)
                } catch (erro: Exception) {
                    null
                }
            }

            _estado.value = RotaViagemUiState(
                pontos = pontos,
                carregado = true,
                pedidoId = viagem?.pedidoId ?: 0,
                destinatarioNome = pedido?.destinatarioNome ?: "",
                metrosCubicos = viagem?.metrosCubicos ?: 0.0,
                dataInicio = viagem?.dataInicio,
                dataFim = viagem?.dataFim,
                kmPercorridos = viagem?.kmPercorridos
            )
        }
    }
}
```

- [ ] **Step 3: Layout — painel de detalhes no topo**

Em `gerencial/src/main/res/layout/activity_rota_viagem.xml`, o arquivo hoje **não** tem `xmlns:tools` declarado na raiz — adicionar antes de usar `tools:text` abaixo:
```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
```

Depois, adicionar o painel logo depois do `<fragment>` do mapa (antes do `textSemPontos`):
```xml
    <LinearLayout
        android:id="@+id/painelDetalhes"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top"
        android:orientation="vertical"
        android:background="#E6FFFFFF"
        android:padding="12dp">

        <TextView
            android:id="@+id/textPedido"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textStyle="bold"
            tools:text="Pedido nº 4 — Construtora Alvorada Ltda" />

        <TextView
            android:id="@+id/textQuantidade"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            tools:text="6 m³" />

        <TextView
            android:id="@+id/textPeriodo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            tools:text="Início 20/09/2026 14:30 · Fim 20/09/2026 16:10" />

        <TextView
            android:id="@+id/textKm"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textStyle="bold"
            tools:text="18,4 km percorridos" />

    </LinearLayout>
```

- [ ] **Step 4: Activity — popular o painel**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/RotaViagemActivity.kt`:

Adicionar imports:
```kotlin
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

Adicionar campo (junto aos outros `lateinit var`):
```kotlin
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private lateinit var textPedido: TextView
    private lateinit var textQuantidade: TextView
    private lateinit var textPeriodo: TextView
    private lateinit var textKm: TextView
```

No `onCreate`, depois de `textSemPontos = findViewById(R.id.textSemPontos)`:
```kotlin
        textPedido = findViewById(R.id.textPedido)
        textQuantidade = findViewById(R.id.textQuantidade)
        textPeriodo = findViewById(R.id.textPeriodo)
        textKm = findViewById(R.id.textKm)
```

No início de `exibirEstado` (logo depois do `if (!estado.carregado) return`), popular o painel:
```kotlin
        textPedido.text = getString(R.string.rota_pedido_formato, estado.pedidoId, estado.destinatarioNome)
        textQuantidade.text = getString(R.string.rota_quantidade_formato, formatarM3(estado.metrosCubicos))
        val inicio = estado.dataInicio?.let { formatoData.format(Date(it)) } ?: ""
        val fim = estado.dataFim?.let { formatoData.format(Date(it)) }
        textPeriodo.text = if (fim != null) {
            getString(R.string.rota_periodo_formato, inicio, fim)
        } else {
            getString(R.string.rota_inicio_formato, inicio)
        }
        textKm.text = estado.kmPercorridos?.let { getString(R.string.rota_km_formato, formatarKm(it)) } ?: ""
        textKm.visibility = if (estado.kmPercorridos != null) View.VISIBLE else View.GONE
```

- [ ] **Step 5: Novas strings**

Em `gerencial/src/main/res/values/strings.xml`, acrescentar perto de `rota_titulo`:
```xml
    <string name="rota_pedido_formato">Pedido nº %1$d — %2$s</string>
    <string name="rota_quantidade_formato">%1$s m³</string>
    <string name="rota_inicio_formato">Início %1$s</string>
    <string name="rota_periodo_formato">Início %1$s · Fim %2$s</string>
    <string name="rota_km_formato">%1$s km percorridos</string>
```

- [ ] **Step 6: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Teste manual no celular**

No app gerencial, abrir o histórico de um pedido com viagem já iniciada → tocar em "ver rota" → confirmar que o painel no topo mostra pedido/cliente, m³, início (e fim + km se já concluída).

- [ ] **Step 8: Commit**

```bash
git add gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/backend/ViagemRepositorio.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/RotaViagemViewModel.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/RotaViagemActivity.kt gerencial/src/main/res/layout/activity_rota_viagem.xml gerencial/src/main/res/values/strings.xml
git commit -m "Painel de detalhes (pedido, m³, período, km) na tela de rota do gerencial"
```

---

## Task 8: Total de km do pedido — `HistoricoPedidoActivity`

**Files:**
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/HistoricoPedidoViewModel.kt`
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/HistoricoPedidoActivity.kt`
- Modify: `gerencial/src/main/res/layout/activity_historico_pedido.xml`
- Modify: `gerencial/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ViagemRemota.kmPercorridos` (Task 2), `StatusViagem.CANCELADA` (já existe).

- [ ] **Step 1: `HistoricoPedidoUiState` ganha o total de km**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/HistoricoPedidoViewModel.kt`, acrescentar o campo na data class:
```kotlin
    val status: StatusPedido = StatusPedido.PENDENTE,
    val totalKm: Double = 0.0,
    val viagens: List<Pair<String, ViagemRemota>> = emptyList(),
```

No método `carregar`, calcular o total (soma das viagens não canceladas) e passar pro `HistoricoPedidoUiState`:
```kotlin
                val totalKm = viagens
                    .filter { (_, viagem) -> viagem.status != StatusViagem.CANCELADA.name }
                    .sumOf { (_, viagem) -> viagem.kmPercorridos ?: 0.0 }

                _estado.value = HistoricoPedidoUiState(
                    pedidoId = pedidoId,
                    destinatarioNome = pedido.destinatarioNome,
                    m3Alocados = pedido.m3Alocados,
                    quantidadeTotalM3 = pedido.quantidadeTotalM3,
                    saldo = pedido.quantidadeTotalM3 - pedido.m3Alocados,
                    dataEntrega = pedido.dataEntrega,
                    status = status,
                    totalKm = totalKm,
                    viagens = viagens
                )
```

- [ ] **Step 2: Exibir na Activity**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/HistoricoPedidoActivity.kt`:

Adicionar import: `import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm`

Adicionar campo: `private lateinit var textKmTotal: TextView`

No `onCreate`, depois de `textProgresso = findViewById(R.id.textProgresso)`:
```kotlin
        textKmTotal = findViewById(R.id.textKmTotal)
```

Em `exibirEstado`, depois de popular `textProgresso.text`:
```kotlin
        textKmTotal.text = getString(R.string.historico_km_total_formato, formatarKm(estado.totalKm))
```

- [ ] **Step 3: Layout**

Em `gerencial/src/main/res/layout/activity_historico_pedido.xml`, acrescentar depois do `textProgresso`:
```xml
        <TextView
            android:id="@+id/textKmTotal"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp" />
```

- [ ] **Step 4: String nova**

Em `gerencial/src/main/res/values/strings.xml`:
```xml
    <string name="historico_km_total_formato">%1$s km percorridos (todas as viagens)</string>
```

- [ ] **Step 5: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Teste manual no celular**

Abrir o histórico de um pedido com pelo menos uma viagem concluída → confirmar que aparece "X km percorridos (todas as viagens)" com o valor certo (soma das viagens não canceladas).

- [ ] **Step 7: Commit**

```bash
git add gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/viewmodel/HistoricoPedidoViewModel.kt gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/HistoricoPedidoActivity.kt gerencial/src/main/res/layout/activity_historico_pedido.xml gerencial/src/main/res/values/strings.xml
git commit -m "Mostra o total de km percorridos do pedido (soma das viagens não canceladas)"
```

---

## Task 9: Km por viagem na lista do histórico — `ViagemAdapter`

**Files:**
- Modify: `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ViagemAdapter.kt`
- Modify: `gerencial/src/main/res/layout/item_viagem.xml`
- Modify: `gerencial/src/main/res/values/strings.xml`

- [ ] **Step 1: Layout — novo TextView**

Em `gerencial/src/main/res/layout/item_viagem.xml`, acrescentar depois do `textDetalhes` (antes do `botaoCancelar`):
```xml
    <TextView
        android:id="@+id/textKm"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:visibility="gone"
        tools:text="18,4 km percorridos" />
```

- [ ] **Step 2: Adapter — popular e mostrar/esconder**

Em `gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ViagemAdapter.kt`:

Adicionar import: `import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm`

No `ViewHolder`, acrescentar: `val textKm: TextView = view.findViewById(R.id.textKm)`

No fim de `onBindViewHolder` (depois de montar `holder.textDetalhes.text`, antes do cálculo de `podeCancelar`):
```kotlin
        val km = item.kmPercorridos
        if (km != null) {
            holder.textKm.text = contexto.getString(R.string.viagem_km_formato, formatarKm(km))
            holder.textKm.visibility = View.VISIBLE
        } else {
            holder.textKm.visibility = View.GONE
        }
```

- [ ] **Step 3: String nova**

Em `gerencial/src/main/res/values/strings.xml`, perto de `viagem_detalhes_cancelada_formato`:
```xml
    <string name="viagem_km_formato">%1$s km percorridos</string>
```

- [ ] **Step 4: Build**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Teste manual no celular**

No histórico de um pedido, confirmar que viagens concluídas (e canceladas com km gravado) mostram a linha de km; viagens ainda atribuídas/em rota sem km gravado não mostram a linha (fica escondida).

- [ ] **Step 6: Commit**

```bash
git add gerencial/src/main/java/br/com/anderson/meuprimeiroprojeto/gerencial/ui/ViagemAdapter.kt gerencial/src/main/res/layout/item_viagem.xml gerencial/src/main/res/values/strings.xml
git commit -m "Mostra o km percorrido de cada viagem na lista do histórico do pedido"
```

---

## Self-Review (já aplicado ao escrever o plano)

**Cobertura da spec:**
- Telas/navegação do motorista → Tasks 5 e 6.
- `RastreamentoService` multi-viagem → Task 4.
- Cálculo de km (Haversine) → Task 3.
- `kmPercorridos` no modelo + total do pedido calculado (não gravado) + exclusão de canceladas → Tasks 2, 8.
- Km de viagem cancelada gravado, mas fora do total → Task 6 (Step 2, `escutarCancelamento`) e Task 8 (filtro).
- Painel de detalhes na `RotaViagemActivity` → Task 7.
- Km por item no histórico → Task 9.
- Dependência nova do Google Maps no `app` → Task 1, sinalizada explicitamente.

**Sem placeholders:** todo step tem código completo, sem "TODO"/"implementar depois".

**Consistência de tipos:** `RastreamentoService.iniciar/encerrar(context, viagemId)` usado igual nas Tasks 4 e 6; `PontoGpsRepositorio.calcularKmPercorridos(context, viagemId): Double` usado igual nas Tasks 3 e 6; `ViagemRepositorio.finalizarViagem(viagemId, kmPercorridos)` com a nova assinatura usada só na Task 6 (única chamadora).
