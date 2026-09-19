package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um ponto de GPS capturado durante uma viagem em rota. Fica no banco local primeiro
 * (fila offline) e [sincronizado] só vira true depois de confirmado no Firestore.
 * viagemId é o id do documento da viagem no Firestore (não é mais gerado pelo Room).
 */
@Entity(tableName = "pontos_gps")
data class PontoGps(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val viagemId: String,
    val latitude: Double,
    val longitude: Double,
    val horario: Long,
    val sincronizado: Boolean = false
)
