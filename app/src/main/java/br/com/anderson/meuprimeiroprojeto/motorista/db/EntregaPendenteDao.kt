package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * As operações na tabela de entregas pendentes (a fila de confirmações feitas sem sinal).
 *
 * DAO quer dizer "Data Access Object" (objeto de acesso a dados). É uma `interface`: aqui só
 * escrevemos o nome de cada função e, nas anotações, o que ela deve fazer no banco. O Room gera
 * sozinho o código de verdade durante o build.
 *
 * Todas as funções são `suspend`: o Android não deixa acessar o banco na thread principal (a que
 * desenha a tela), porque isso poderia travar o app. Com `suspend`, o acesso acontece "por fora".
 */
@Dao
interface EntregaPendenteDao {

    /**
     * Coloca a entrega na fila. Se essa viagem já estava na fila, a linha antiga é substituída
     * (`OnConflictStrategy.REPLACE`), então nunca fica repetida.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(entrega: EntregaPendente)

    /** Devolve todas as entregas que ainda não foram aceitas pelo servidor. */
    @Query("SELECT * FROM entregas_pendentes")
    suspend fun buscarPendentes(): List<EntregaPendente>

    /**
     * Tira a viagem da fila, porque o servidor já aceitou a confirmação. O `:viagemId` dentro do
     * SQL é trocado pelo valor do parâmetro de mesmo nome.
     */
    @Query("DELETE FROM entregas_pendentes WHERE viagemId = :viagemId")
    suspend fun remover(viagemId: String)
}
