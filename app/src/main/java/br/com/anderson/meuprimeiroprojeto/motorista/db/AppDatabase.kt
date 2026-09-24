package br.com.anderson.meuprimeiroprojeto.motorista.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * O banco de dados local do app do motorista, guardado dentro do próprio celular.
 *
 * Ele usa o Room, uma biblioteca do Android que facilita usar o SQLite (o banco de dados que já
 * vem em todo celular Android). Com o Room, a gente descreve as tabelas como classes Kotlin
 * (`@Entity`) e as consultas como funções (`@Dao`), e ele gera o código SQL sozinho.
 *
 * Os dados "de verdade" (pedidos, viagens, roteiros) ficam na nuvem, no Firestore. Este banco
 * local serve para duas filas de coisas que ainda não conseguiram subir para a nuvem por falta
 * de sinal:
 * - [PontoGps]: as posições do caminhão durante o roteiro;
 * - [EntregaPendente]: as confirmações de entrega feitas sem sinal.
 *
 * A anotação `@Database` diz quais tabelas existem (`entities`) e a versão do banco. A versão
 * precisa aumentar toda vez que uma tabela muda.
 *
 * `abstract class`: é uma classe "incompleta", que não pode ser criada direto. O Room completa o
 * código dela sozinho, durante o build.
 */
@Database(entities = [PontoGps::class, EntregaPendente::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /** Dá acesso às consultas da tabela de pontos de GPS. O Room escreve o código desta função. */
    abstract fun pontoGpsDao(): PontoGpsDao

    /** Dá acesso às consultas da fila de entregas pendentes. O Room escreve o código desta função. */
    abstract fun entregaPendenteDao(): EntregaPendenteDao

    // "companion object" guarda coisas que pertencem à classe em si, e não a cada objeto dela.
    // É parecido com o "static" do Java: chama-se direto pelo nome, `AppDatabase.obter(contexto)`.
    companion object {
        // @Volatile garante que, se duas partes do app (duas "threads") olharem esta variável ao
        // mesmo tempo, as duas enxerguem o valor mais recente.
        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Devolve o banco de dados, criando-o só na primeira vez que for pedido. Nas outras vezes
         * devolve o mesmo, já aberto (isso se chama padrão "singleton": uma instância só).
         *
         * Abrir o banco é caro, e ter dois abertos ao mesmo tempo pode dar problema; por isso só
         * existe um. O `synchronized` funciona como uma porta que só deixa uma thread entrar de
         * cada vez, para duas não criarem o banco ao mesmo tempo.
         *
         * Recebe um `Context` (o "contexto" do Android: dá acesso aos arquivos e recursos do app)
         * porque o Room precisa saber onde criar o arquivo do banco.
         */
        fun obter(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "entregas.db"
                )
                    // Ainda não há usuários em produção; recriar o banco a cada mudança de
                    // schema é mais simples do que escrever migrations nesta fase do projeto.
                    // (Na prática: quando a versão muda, o banco antigo é apagado e criado de novo.)
                    .fallbackToDestructiveMigration(true)
                    .build().also { instancia = it }
            }
    }
}
