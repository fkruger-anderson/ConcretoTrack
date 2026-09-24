package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Uma linha da tabela `pontos_gps` do banco local (Room): uma posição do caminhão, capturada
 * durante um roteiro em rota.
 *
 * Cada ponto é gravado primeiro aqui no celular (assim nada se perde sem sinal) e depois enviado
 * ao Firestore. O campo [sincronizado] diz se ele já foi enviado. Os pontos de um roteiro, em
 * ordem, formam o trajeto desenhado no mapa e servem para calcular os km rodados.
 *
 * `@Entity` diz ao Room que esta classe é uma tabela; cada campo vira uma coluna.
 */
@Entity(tableName = "pontos_gps")
data class PontoGps(
    /**
     * Número único do ponto. `autoGenerate = true` faz o banco dar o número sozinho (1, 2, 3...)
     * na hora de gravar; por isso o valor padrão é 0.
     */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Id do roteiro (documento no Firestore) a que este ponto pertence. */
    val roteiroId: String,
    /** Coordenadas do caminhão no momento da leitura. */
    val latitude: Double,
    val longitude: Double,
    /** Quando a posição foi lida (milissegundos desde 1º/01/1970). */
    val horario: Long,
    /** `false` enquanto o ponto só existe no celular; `true` depois que o Firestore confirmou. */
    val sincronizado: Boolean = false
)
