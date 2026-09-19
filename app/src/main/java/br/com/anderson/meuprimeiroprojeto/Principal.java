package br.com.anderson.meuprimeiroprojeto;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class Principal extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void selecionarPedra(View view){
        verificarGanhador("pedra");
    }
    public void selecionarPapel(View view){
        verificarGanhador("papel");
    }
    public void selecionarTesoura(View view){
        verificarGanhador("tesoura");
    }

    private void verificarGanhador(String escolhaUsuario){
        String escolhaApp = gerarEscolhaAleatoriaApp();
        TextView textoResultado = findViewById(R.id.txtResultado);

        if ( escolhaUsuario.equals(escolhaApp) ) {
            textoResultado.setText("Empate!");
        } else if (
            (escolhaUsuario.equals("pedra") && escolhaApp.equals("tesoura")) ||
            (escolhaUsuario.equals("papel") && escolhaApp.equals("pedra")) ||
            (escolhaUsuario.equals("tesoura") && escolhaApp.equals("papel"))
        ) {
            textoResultado.setText("Você ganhou!");
        } else {
            textoResultado.setText("Você perdeu!");
        }
    }

    private String gerarEscolhaAleatoriaApp() {
        String[] opcoes = {"pedra", "papel", "tesoura"};
        int indiceAleatorio = new Random().nextInt(opcoes.length);

        ImageView imageApp = findViewById(R.id.imgApp);
        String escolhaApp = opcoes[indiceAleatorio];
        switch ( escolhaApp ) {
            case "pedra":
                imageApp.setImageResource(R.drawable.pedra);
                break;
            case "papel":
                imageApp.setImageResource(R.drawable.papel);
                break;
            case "tesoura":
                imageApp.setImageResource(R.drawable.tesoura);
                break;
        };

        return escolhaApp;
    }
}
