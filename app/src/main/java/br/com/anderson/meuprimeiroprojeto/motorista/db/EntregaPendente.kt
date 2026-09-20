package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fila local de confirmações de entrega que não conseguiram ser enviadas ao Firestore na hora
 * (sem conexão). [dataFim] é o horário real em que o motorista marcou a entrega — guardado aqui
 * pra não ficar desatualizado se o reenvio automático só conseguir completar minutos depois,
 * quando a conexão voltar. RastreamentoService tenta reenviar a cada atualização de localização
 * (10 em 10s) enquanto o roteiro estiver em rota, até conseguir.
 */
@Entity(tableName = "entregas_pendentes")
data class EntregaPendente(
    @PrimaryKey val viagemId: String,
    val dataFim: Long
)
