package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Acesso ao Room para a fila de confirmações de entrega pendentes de reenviar ao Firestore. */
@Dao
interface EntregaPendenteDao {

    /** Marca esta viagem como tendo uma confirmação pendente. Substitui se já existia (mesma viagemId). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(entrega: EntregaPendente)

    /** Todas as confirmações ainda não enviadas com sucesso ao Firestore. */
    @Query("SELECT * FROM entregas_pendentes")
    suspend fun buscarPendentes(): List<EntregaPendente>

    /** Tira a viagem da fila — a confirmação já foi aceita pelo servidor. */
    @Query("DELETE FROM entregas_pendentes WHERE viagemId = :viagemId")
    suspend fun remover(viagemId: String)
}
