package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Uma linha da tabela `entregas_pendentes` do banco local (Room): uma confirmação de entrega que
 * ainda não conseguiu chegar ao Firestore.
 *
 * Quando o motorista toca em "entregue" sem sinal, a confirmação fica guardada aqui. O
 * `RastreamentoService` tenta reenviar a fila a cada nova posição de GPS (de 10 em 10 segundos)
 * enquanto o roteiro estiver em rota, até o servidor aceitar; aí a linha é apagada.
 *
 * `@Entity` diz ao Room que esta classe é uma tabela. Cada campo vira uma coluna.
 */
@Entity(tableName = "entregas_pendentes")
data class EntregaPendente(
    /**
     * Id da viagem entregue. `@PrimaryKey` (chave primária) quer dizer que não pode haver duas
     * linhas com o mesmo valor: cada viagem aparece no máximo uma vez na fila.
     */
    @PrimaryKey val viagemId: String,
    /**
     * O horário em que o motorista marcou a entrega. Fica guardado para que a data gravada no
     * servidor seja a real, mesmo se o reenvio só der certo minutos depois.
     */
    val dataFim: Long
)
