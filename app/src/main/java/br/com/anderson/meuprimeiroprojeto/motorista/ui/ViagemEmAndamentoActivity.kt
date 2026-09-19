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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagemEmAndamentoUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagemEmAndamentoViewModel

/** Tela exibida enquanto a viagem está em rota: liga o serviço de GPS e permite finalizar. */
class ViagemEmAndamentoActivity : AppCompatActivity() {

    companion object {
        /** Chave do extra usado para informar o id da viagem ao abrir esta Activity. */
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private val viewModel: ViagemEmAndamentoViewModel by viewModels()

    private lateinit var viagemId: String
    private lateinit var textPontos: TextView
    private lateinit var botaoAjustarBateria: Button
    private lateinit var textBateria: TextView

    /** Trata o resultado do pedido de permissão de localização (fina/aproximada e notificações). */
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

    /** Trata o resultado do pedido de permissão de localização em segundo plano. */
    private val launcherPermissaoSegundoPlano =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Com o foreground service do tipo "location" o rastreamento já funciona mesmo
            // sem essa permissão; ela é só um reforço para o app continuar rastreando caso o
            // Android trate o serviço como encerrado antes da hora.
            iniciarServico()
        }

    /**
     * Lê o id da viagem, inicia a ViewModel, checa a otimização de bateria e dispara o pedido
     * de permissões de localização (que, se concedidas, liga o serviço de GPS).
     */
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

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.iniciar(viagemId)

        verificarOtimizacaoDeBateria()
        pedirPermissaoDeLocalizacaoEIniciar()
    }

    /** Atualiza os textos da tela e trata um cancelamento remoto vindo do gerente, se houver. */
    private fun exibirEstado(estado: ViagemEmAndamentoUiState) {
        findViewById<TextView>(R.id.textDestinatario).text = estado.destinatarioNome
        textPontos.text = getString(R.string.andamento_pontos_formato, estado.totalPontos, estado.pontosPendentes)

        estado.cancelamento?.getConteudoSeNaoTratado()?.let { motivo -> tratarCancelamentoRemoto(motivo) }
    }

    /** Para o serviço de GPS e volta pra lista de viagens quando o gerente cancela a viagem. */
    private fun tratarCancelamentoRemoto(motivo: String) {
        stopService(Intent(this, RastreamentoService::class.java))
        Toast.makeText(this, getString(R.string.andamento_viagem_cancelada, motivo), Toast.LENGTH_LONG).show()

        val intent = Intent(this, MinhasViagensActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    /** Pede permissão de localização (e notificações, no Android 13+) se ainda não concedida. */
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

    /** Pede a permissão de localização em segundo plano, necessária para rastrear com a tela fechada. */
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

    /** Sobe o foreground service de rastreamento de GPS para esta viagem. */
    private fun iniciarServico() {
        val intent = Intent(this, RastreamentoService::class.java)
            .putExtra(RastreamentoService.EXTRA_VIAGEM_ID, viagemId)
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * Mostra o aviso e o botão para pedir isenção da otimização de bateria, só se o Android
     * ainda não isentou o app — sem isso o sistema pode matar o serviço de GPS em segundo plano.
     */
    private fun verificarOtimizacaoDeBateria() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val ignorandoOtimizacao = powerManager.isIgnoringBatteryOptimizations(packageName)
        botaoAjustarBateria.visibility = if (ignorandoOtimizacao) android.view.View.GONE else android.view.View.VISIBLE
        textBateria.visibility = botaoAjustarBateria.visibility
    }

    /** Abre a tela do sistema para o usuário isentar o app da otimização de bateria. */
    private fun abrirAjusteDeBateria() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    /** Pede à ViewModel para recontar quantos pontos de GPS já foram gravados/sincronizados. */
    private fun atualizarContagemDePontos() {
        viewModel.atualizarContagemDePontos(viagemId)
    }

    /** Reavalia a isenção de bateria e a contagem de pontos toda vez que a tela volta a ficar visível. */
    override fun onResume() {
        super.onResume()
        verificarOtimizacaoDeBateria()
        atualizarContagemDePontos()
    }

    /** Para o serviço de GPS, marca a viagem como concluída e volta pra lista de viagens. */
    private fun finalizarViagem() {
        stopService(Intent(this, RastreamentoService::class.java))
        viewModel.finalizarViagem(viagemId)

        Toast.makeText(this, R.string.andamento_viagem_finalizada, Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MinhasViagensActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}
