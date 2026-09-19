package br.com.anderson.meuprimeiroprojeto.motorista.gps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.aguardar
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.PontoGps
import br.com.anderson.meuprimeiroprojeto.motorista.ui.MinhasViagensActivity
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
 * Serviço em primeiro plano que grava pontos de GPS da viagem em rota.
 * Fica ativo só enquanto a viagem está em rota (iniciado e parado pela UI).
 */
class RastreamentoService : Service() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
        private const val CANAL_ID = "rastreamento_viagem"
        private const val NOTIFICACAO_ID = 1001
        private const val INTERVALO_ATUALIZACAO_MS = 10_000L
    }

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clienteLocalizacao: FusedLocationProviderClient
    private var callbackLocalizacao: LocationCallback? = null
    private var viagemId: String? = null

    override fun onCreate() {
        super.onCreate()
        clienteLocalizacao = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        viagemId = intent?.getStringExtra(EXTRA_VIAGEM_ID)
        if (viagemId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        criarCanalDeNotificacao()
        startForeground(NOTIFICACAO_ID, criarNotificacao(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        iniciarAtualizacoesDeLocalizacao()
        return START_STICKY
    }

    private fun iniciarAtualizacoesDeLocalizacao() {
        val temPermissao = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!temPermissao) {
            stopSelf()
            return
        }

        // Se o serviço já estava rastreando outra viagem, encerra o registro antigo antes de
        // criar um novo — sem isso os dois ficariam ativos ao mesmo tempo (chamadas em duplicidade
        // do FusedLocationProviderClient para o mesmo app costumam ser mescladas de forma
        // imprevisível, então não dá para contar com isso).
        callbackLocalizacao?.let { clienteLocalizacao.removeLocationUpdates(it) }

        val alvoViagemId = viagemId ?: return
        val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_ATUALIZACAO_MS).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(resultado: LocationResult) {
                val localizacao = resultado.lastLocation ?: return
                val horario = System.currentTimeMillis()
                escopo.launch {
                    AppDatabase.obter(applicationContext).pontoGpsDao().inserir(
                        PontoGps(
                            viagemId = alvoViagemId,
                            latitude = localizacao.latitude,
                            longitude = localizacao.longitude,
                            horario = horario
                        )
                    )
                    sincronizarPontosPendentes()
                    atualizarPosicaoAtual(alvoViagemId, localizacao.latitude, localizacao.longitude, horario)
                }
            }
        }
        callbackLocalizacao = callback
        clienteLocalizacao.requestLocationUpdates(pedido, callback, Looper.getMainLooper())
    }

    /**
     * A fila offline: tenta subir para o Firestore os pontos que ainda não foram sincronizados.
     * Se não houver conexão, a primeira falha simplesmente para o loop — os pontos continuam
     * guardados no Room e a tentativa se repete na próxima atualização de localização (10 em 10s).
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
                break
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
            this, 0, Intent(this, MinhasViagensActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
