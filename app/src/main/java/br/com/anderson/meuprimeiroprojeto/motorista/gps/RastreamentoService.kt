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
import br.com.anderson.meuprimeiroprojeto.motorista.backend.RoteiroRepositorio
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
 * O "rastreador" do caminhão: enquanto o roteiro está em rota, lê a posição do GPS a cada 10
 * segundos e guarda.
 *
 * Um `Service` (serviço) é uma parte do app que trabalha sem ter tela. Isso é necessário aqui
 * porque o motorista vai trocar de app (abrir o Google Maps, atender ligação) e apagar a tela, e o
 * rastreamento precisa continuar.
 *
 * Ele é um serviço "em primeiro plano" (foreground): mostra uma notificação fixa enquanto roda.
 * O Android exige isso para qualquer app que use a localização em segundo plano, para o usuário
 * sempre saber que está sendo rastreado. Sem a notificação, o sistema mataria o serviço.
 *
 * A cada posição recebida, o serviço:
 * 1. grava o ponto no banco do celular (Room), para não perder nada sem sinal;
 * 2. atualiza a "última posição" do roteiro no Firestore (é o que o mapa do gerente mostra);
 * 3. tenta enviar ao Firestore os pontos que ficaram para trás (a fila offline);
 * 4. tenta reenviar as confirmações de entrega que ficaram para trás.
 *
 * Só existe um roteiro ativo por motorista, então o serviço rastreia um roteiro de cada vez.
 */
class RastreamentoService : Service() {

    // "companion object": coisas que pertencem à classe, não a um objeto dela (parecido com o
    // "static" do Java). Aqui ficam as constantes e as duas funções para ligar/desligar o serviço.
    companion object {
        /** Nome do "extra" (dado extra enviado junto do Intent) que leva o id do roteiro. */
        const val EXTRA_ROTEIRO_ID = "roteiro_id"
        // As "ações" dizem ao serviço o que fazer quando ele recebe um Intent: começar ou parar.
        private const val ACTION_INICIAR = "br.com.anderson.meuprimeiroprojeto.INICIAR_ROTEIRO"
        private const val ACTION_ENCERRAR = "br.com.anderson.meuprimeiroprojeto.ENCERRAR_ROTEIRO"
        private const val CANAL_ID = "rastreamento_viagem"
        private const val NOTIFICACAO_ID = 1001
        // De quanto em quanto tempo pedir uma posição nova: 10 segundos (em milissegundos).
        private const val INTERVALO_ATUALIZACAO_MS = 10_000L
        private const val TAG = "RastreamentoService"

        /**
         * Liga o rastreamento deste roteiro. Quem chama é a aba Roteiro (ao iniciar o roteiro ou ao
         * reabrir um roteiro que já estava em rota).
         *
         * Monta um `Intent` (um "pedido" para o Android) apontando para este serviço, com a ação
         * INICIAR e o id do roteiro. `startForegroundService` avisa ao Android que o serviço vai
         * mostrar a notificação fixa logo em seguida. Pode ser chamada mais de uma vez sem problema.
         */
        fun iniciar(context: Context, roteiroId: String) {
            val intent = Intent(context, RastreamentoService::class.java)
                .setAction(ACTION_INICIAR)
                .putExtra(EXTRA_ROTEIRO_ID, roteiroId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Desliga o rastreamento deste roteiro. Chamada quando o motorista encerra o roteiro. */
        fun encerrar(context: Context, roteiroId: String) {
            val intent = Intent(context, RastreamentoService::class.java)
                .setAction(ACTION_ENCERRAR)
                .putExtra(EXTRA_ROTEIRO_ID, roteiroId)
            context.startService(intent)
        }
    }

    // O "escopo" onde as coroutines deste serviço rodam. `Dispatchers.IO` manda o trabalho para
    // threads próprias para banco e rede (fora da thread da tela). `SupervisorJob` faz com que, se
    // uma tarefa der erro, as outras continuem.
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // O cliente de localização do Google Play Services (escolhe sozinho entre GPS, Wi-Fi e rede
    // de celular para dar a melhor posição). `lateinit` = será preenchido depois, no onCreate.
    private lateinit var clienteLocalizacao: FusedLocationProviderClient
    // A função que recebe cada posição nova; guardada para poder "desligá-la" depois.
    private var callbackLocalizacao: LocationCallback? = null
    // Id do roteiro sendo rastreado agora, ou null se nenhum.
    private var roteiroAtivo: String? = null

    /**
     * Chamado pelo Android uma única vez, quando o serviço é criado. Aqui só se prepara o cliente de
     * localização.
     */
    override fun onCreate() {
        super.onCreate()
        clienteLocalizacao = LocationServices.getFusedLocationProviderClient(this)
    }

    /**
     * Chamado pelo Android toda vez que alguém chama [iniciar] ou [encerrar] (cada chamada entrega
     * um `Intent` aqui).
     *
     * - Sem id de roteiro no Intent: se não estava rastreando nada, desliga o serviço.
     * - Ação ENCERRAR: se é o roteiro que está sendo rastreado, para o GPS e desliga o serviço
     *   (`stopSelf`). É o caminho que para o serviço quando ele não é mais necessário.
     * - Ação INICIAR: guarda o roteiro, mostra a notificação fixa (`startForeground`) e liga o GPS.
     *
     * O valor devolvido diz ao Android o que fazer se ele precisar matar o serviço por falta de
     * memória. `START_STICKY` = "tente recriar o serviço depois". Nesse caso o Intent chega vazio
     * e o id do roteiro se perde; quem recupera é a aba Roteiro, que chama [iniciar] de novo quando
     * é aberta com um roteiro em rota. `START_NOT_STICKY` = "não precisa recriar".
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
     * Pede ao Android para receber a posição do caminhão a cada 10 segundos, com alta precisão.
     *
     * Primeiro confere se o app tem permissão de localização; sem ela, desliga o serviço (o
     * Android não entregaria posição nenhuma).
     *
     * O `LocationCallback` é um objeto com uma função ([LocationCallback.onLocationResult]) que o
     * Android chama sozinho a cada posição nova. Como gravar no banco e na internet demora, esse
     * trabalho é jogado para uma coroutine (`escopo.launch`), que roda fora da thread da tela.
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
            /**
             * Chamada pelo Android a cada posição nova. Grava o ponto no Room e depois cuida do
             * Firestore (posição atual e filas offline).
             */
            override fun onLocationResult(resultado: LocationResult) {
                val localizacao = resultado.lastLocation ?: return
                val horario = System.currentTimeMillis()
                escopo.launch {
                    AppDatabase.obter().pontoGpsDao().inserir(
                        PontoGps(
                            roteiroId = alvoRoteiroId,
                            latitude = localizacao.latitude,
                            longitude = localizacao.longitude,
                            horario = horario
                        )
                    )
                    atualizarPosicaoAtual(alvoRoteiroId, localizacao.latitude, localizacao.longitude, horario)
                    sincronizarPontosPendentes()
                    sincronizarEntregasPendentes()
                }
            }
        }
        callbackLocalizacao = callback
        clienteLocalizacao.requestLocationUpdates(pedido, callback, Looper.getMainLooper())
    }

    /**
     * A fila offline de pontos de GPS: tenta enviar ao Firestore cada ponto que ainda está só no
     * celular.
     *
     * Cada ponto é tentado separadamente: se um falhar (sem internet, erro do servidor), os
     * outros continuam sendo tentados. O que falhou continua marcado como pendente no Room e entra
     * de novo na próxima tentativa, 10 segundos depois.
     *
     * O nome do documento é "ponto_" + o número do ponto no Room. Assim, se o mesmo ponto for
     * enviado duas vezes, ele só é sobrescrito, sem ficar repetido no Firestore.
     */
    private suspend fun sincronizarPontosPendentes() {
        val dao = AppDatabase.obter().pontoGpsDao()
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
     * A fila offline de confirmações de entrega: tenta reenviar cada entrega que o motorista
     * marcou sem sinal (ver [RoteiroRepositorio.marcarEntregue]). Quando o servidor aceita, a
     * entrega sai da fila. Mesma lógica da fila de pontos: cada uma é tentada separadamente.
     */
    private suspend fun sincronizarEntregasPendentes() {
        val dao = AppDatabase.obter().entregaPendenteDao()
        for (entrega in dao.buscarPendentes()) {
            try {
                RoteiroRepositorio.confirmarEntregaNoServidor(entrega.viagemId, entrega.dataFim)
                dao.remover(entrega.viagemId)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao sincronizar confirmação de entrega da viagem ${entrega.viagemId}", erro)
            }
        }
    }

    /**
     * Grava a posição atual no próprio documento do roteiro. É esse campo que o mapa do app
     * gerencial fica "escutando" para mover o caminhão na tela.
     *
     * Diferente dos pontos, isto não entra em fila: se falhar por falta de internet, tudo bem,
     * porque daqui a 10 segundos chega uma posição mais nova mesmo.
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

    /**
     * Cria o "canal" da notificação. Desde o Android 8, toda notificação precisa pertencer a um
     * canal, que o usuário pode configurar nas opções do sistema (som, importância). Criar um canal
     * que já existe não faz nada, então pode ser chamado sempre.
     */
    private fun criarCanalDeNotificacao() {
        val gerenciador = getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(
            CANAL_ID, getString(R.string.notificacao_rastreamento_canal), NotificationManager.IMPORTANCE_LOW
        )
        gerenciador.createNotificationChannel(canal)
    }

    /**
     * Monta a notificação fixa que fica visível enquanto o rastreamento roda. `setOngoing(true)`
     * impede o motorista de dispensá-la arrastando para o lado. Tocar na notificação abre a tela
     * inicial do app (o `PendingIntent` é um "Intent guardado para ser usado depois").
     */
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

    /**
     * Chamado pelo Android quando o serviço vai ser destruído. Libera o que foi criado: para de
     * receber posições do GPS e cancela as coroutines que ainda estavam rodando.
     */
    override fun onDestroy() {
        super.onDestroy()
        callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }
        escopo.cancel()
    }

    /**
     * Um serviço pode ser "ligado" a uma tela (bind) para conversar com ela o tempo todo. Este não
     * usa isso: ele só é iniciado e parado por Intents. Devolver `null` quer dizer "não aceito
     * ligação".
     */
    override fun onBind(intent: Intent?): IBinder? = null
}
