package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

/** Tela exibida enquanto a viagem está em rota: liga o serviço de GPS e permite finalizar. */
class ViagemEmAndamentoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private lateinit var viagemId: String
    private lateinit var textPontos: TextView
    private lateinit var botaoAjustarBateria: Button
    private lateinit var textBateria: TextView
    private var registroCancelamento: ListenerRegistration? = null
    private var jaTratouCancelamento = false

    private val launcherPermissoesLocalizacao =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultados ->
            val localizacaoConcedida = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (localizacaoConcedida) {
                pedirPermissaoDeSegundoPlano()
            } else {
                findViewById<TextView>(R.id.textAvisoPermissao).apply {
                    text = getString(R.string.andamento_permissao_negada)
                    visibility = android.view.View.VISIBLE
                }
            }
        }

    private val launcherPermissaoSegundoPlano =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Com o foreground service do tipo "location" o rastreamento já funciona mesmo
            // sem essa permissão; ela é só um reforço para o app continuar rastreando caso o
            // Android trate o serviço como encerrado antes da hora.
            iniciarServico()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viagem_em_andamento)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        textPontos = findViewById(R.id.textPontos)
        botaoAjustarBateria = findViewById(R.id.botaoAjustarBateria)
        textBateria = findViewById(R.id.textBateria)

        findViewById<Button>(R.id.botaoAtualizarContagem).setOnClickListener { atualizarContagemDePontos() }
        botaoAjustarBateria.setOnClickListener { abrirAjusteDeBateria() }
        findViewById<Button>(R.id.botaoFinalizar).setOnClickListener { finalizarViagem() }

        carregarDadosDoPedido()
        verificarOtimizacaoDeBateria()
        pedirPermissaoDeLocalizacaoEIniciar()
        escutarCancelamento()
    }

    /**
     * O gerente pode cancelar a viagem pelo app dele enquanto o motorista está rodando —
     * sem isso o GPS ficaria gravando pontos de uma viagem que não existe mais pro gerente.
     * Escutar o próprio documento é mais simples que ficar consultando de tempos em tempos.
     */
    private fun escutarCancelamento() {
        registroCancelamento = Firebase.firestore.collection("viagens").document(viagemId)
            .addSnapshotListener { snapshot, _ ->
                val viagem = snapshot?.toObject(ViagemRemota::class.java) ?: return@addSnapshotListener
                if (viagem.status == StatusViagem.CANCELADA.name && !jaTratouCancelamento) {
                    jaTratouCancelamento = true
                    tratarCancelamentoRemoto(viagem.motivoCancelamento)
                }
            }
    }

    private fun tratarCancelamentoRemoto(motivo: String?) {
        stopService(Intent(this, RastreamentoService::class.java))
        Toast.makeText(
            this,
            getString(R.string.andamento_viagem_cancelada, motivo ?: ""),
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(this, MinhasViagensActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun carregarDadosDoPedido() {
        lifecycleScope.launch {
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
            findViewById<TextView>(R.id.textDestinatario).text = pedido?.destinatarioNome ?: ""
        }
    }

    private fun pedirPermissaoDeLocalizacaoEIniciar() {
        val jaTemLocalizacao = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (jaTemLocalizacao) {
            pedirPermissaoDeSegundoPlano()
            return
        }

        val permissoes = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissoes.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcherPermissoesLocalizacao.launch(permissoes.toTypedArray())
    }

    private fun pedirPermissaoDeSegundoPlano() {
        val jaTemSegundoPlano = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (jaTemSegundoPlano) {
            iniciarServico()
        } else {
            launcherPermissaoSegundoPlano.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun iniciarServico() {
        val intent = Intent(this, RastreamentoService::class.java)
            .putExtra(RastreamentoService.EXTRA_VIAGEM_ID, viagemId)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun verificarOtimizacaoDeBateria() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val ignorandoOtimizacao = powerManager.isIgnoringBatteryOptimizations(packageName)
        botaoAjustarBateria.visibility = if (ignorandoOtimizacao) android.view.View.GONE else android.view.View.VISIBLE
        textBateria.visibility = botaoAjustarBateria.visibility
    }

    private fun abrirAjusteDeBateria() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun atualizarContagemDePontos() {
        lifecycleScope.launch {
            val dao = AppDatabase.obter(this@ViagemEmAndamentoActivity).pontoGpsDao()
            val total = dao.contarPorViagem(viagemId)
            val pendentes = dao.contarPendentesPorViagem(viagemId)
            textPontos.text = getString(R.string.andamento_pontos_formato, total, pendentes)
        }
    }

    override fun onResume() {
        super.onResume()
        verificarOtimizacaoDeBateria()
        atualizarContagemDePontos()
    }

    private fun finalizarViagem() {
        jaTratouCancelamento = true
        stopService(Intent(this, RastreamentoService::class.java))
        // Não espera confirmação do servidor — funciona mesmo sem sinal (ver o comentário em
        // ViagemRepositorio.finalizarViagem).
        ViagemRepositorio.finalizarViagem(viagemId)

        Toast.makeText(this, R.string.andamento_viagem_finalizada, Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MinhasViagensActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        registroCancelamento?.remove()
    }
}
