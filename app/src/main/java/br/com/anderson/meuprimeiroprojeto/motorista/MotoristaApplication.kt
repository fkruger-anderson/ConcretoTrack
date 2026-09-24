package br.com.anderson.meuprimeiroprojeto.motorista

import android.app.Application
import br.com.anderson.meuprimeiroprojeto.motorista.db.AppDatabase
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista

/**
 * A classe que representa o app do motorista inteiro (e não uma tela só).
 *
 * O Android cria um objeto desta classe uma única vez, logo que o app começa a rodar, ANTES de
 * qualquer tela ou serviço. Para isso ela precisa estar registrada no `AndroidManifest.xml`
 * (atributo `android:name` da tag `<application>`).
 *
 * É o lugar certo para preparar coisas que o app inteiro usa e que precisam de um `Context` (o
 * "contexto" do Android, que dá acesso a arquivos e configurações): o banco local (Room) e as
 * preferências (SharedPreferences). Preparando aqui, uma vez só, o resto do código — principalmente
 * os ViewModels — não precisa carregar um `Context` de um lado para o outro. É a regra da disciplina:
 * "ViewModel não importa Context".
 */
class MotoristaApplication : Application() {

    /**
     * Chamado pelo Android quando o app começa a rodar. Prepara o banco local e as preferências.
     * Nenhum dos dois lê o disco aqui (isso só acontece no primeiro uso), então a abertura do app
     * não fica mais lenta.
     */
    override fun onCreate() {
        super.onCreate()
        AppDatabase.inicializar(this)
        SessaoMotorista.inicializar(this)
    }
}
