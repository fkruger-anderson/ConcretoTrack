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
                    sincronizarEntregasPendentes()
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
     * Fila offline de confirmações de entrega: tenta reenviar cada uma que ainda não foi
     * confirmada no servidor (por exemplo, porque o motorista marcou como entregue sem sinal —
     * ver [RoteiroRepositorio.marcarEntregue]). Mesma lógica de tentativa independente da fila
     * de pontos de GPS logo acima.
     */
    private suspend fun sincronizarEntregasPendentes() {
        val dao = AppDatabase.obter(applicationContext).entregaPendenteDao()
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
