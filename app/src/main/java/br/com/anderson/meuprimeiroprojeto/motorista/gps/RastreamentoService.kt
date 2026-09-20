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
                return START_NOT_STICKY
            }
            // Ainda sobra outra viagem ativa: o rastreamento continua valendo, então o Android
            // deve tentar recriar o serviço se ele for morto.
            return START_STICKY
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

    override fun onDestroy() {
        super.onDestroy()
        callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }
        escopo.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
