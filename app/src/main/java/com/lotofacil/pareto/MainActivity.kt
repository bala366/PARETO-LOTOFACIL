package com.lotofacil.pareto

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var analyzeButton: Button
    private lateinit var suggestButton: Button
    private lateinit var generateButton: Button
    private lateinit var backtestButton: Button
    private var draws: List<List<Int>> = emptyList()

    companion object {
        private const val REQUEST_OPEN_TXT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val purple = Color.rgb(91, 35, 138)
        val green = Color.rgb(66, 201, 107)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(247, 242, 251))
        }

        val header = TextView(this).apply {
            text = "☘  LOTOFÁCIL PARETO"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(purple)
            setPadding(0, 8, 0, 20)
        }

        val subtitle = TextView(this).apply {
            text = "Matriz posicional P01–P15 • perímetro móvel de 10 concursos • BlackTest cego"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 18)
        }

        val loadButton = actionButton("CARREGAR RESULTADOS TXT", purple)
        analyzeButton = actionButton("ANALISAR PARETO — 10 CONCURSOS", purple).apply { isEnabled = false }
        suggestButton = actionButton("SUGERIR 1 JOGO PARETO", green).apply { isEnabled = false }
        generateButton = actionButton("GERAR 100 JOGOS", purple).apply { isEnabled = false }
        backtestButton = actionButton("BLACKTEST CEGO — 100 CONCURSOS", purple).apply { isEnabled = false }

        output = TextView(this).apply {
            text = "Carregue o histórico da Lotofácil em TXT.\n\nO resultado futuro nunca entra no BlackTest cego."
            textSize = 15f
            setTextColor(Color.rgb(45, 45, 45))
            setPadding(10, 24, 10, 24)
        }

        root.addView(header)
        root.addView(subtitle)
        root.addView(loadButton)
        root.addView(analyzeButton)
        root.addView(suggestButton)
        root.addView(generateButton)
        root.addView(backtestButton)
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        loadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            startActivityForResult(intent, REQUEST_OPEN_TXT)
        }

        analyzeButton.setOnClickListener { showMatrix() }
        suggestButton.setOnClickListener { showSuggestion() }
        generateButton.setOnClickListener { generateGames() }
        backtestButton.setOnClickListener { runBlindBacktest() }
    }

    private fun actionButton(label: String, background: Int): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(background)
        isAllCaps = false
    }

    private fun showMatrix() {
        val matrix = ParetoEngine.matrix(draws, 10)
        output.text = buildString {
            appendLine("MATRIZ PARETO — ÚLTIMOS 10 CONCURSOS")
            appendLine("----------------------------------------")
            matrix.forEach { stat ->
                val leaders = stat.ranked.take(4).joinToString("   ") { e ->
                    "%02d=%d%%".format(e.key, (100.0 * e.value / stat.total).toInt())
                }
                appendLine("P%02d  %s".format(stat.position, leaders))
            }
            appendLine()
            appendLine("Quanto maior a concentração, maior a dominância daquela dezena naquela posição.")
        }
    }

    private fun showSuggestion() {
        output.text = "Calculando sugestão Pareto..."
        thread {
            val candidate = ParetoEngine.suggestedGame(draws)
            runOnUiThread {
                output.text = if (candidate == null) {
                    "Histórico insuficiente para gerar a sugestão."
                } else {
                    "JOGO SUGERIDO — PARETO MULTIJANELA\n\n" +
                        candidate.numbers.joinToString(" ") { "%02d".format(it) } +
                        "\n\nScore interno: %.4f\n\nA sugestão é estatística; não representa garantia de premiação.".format(candidate.score)
                }
            }
        }
    }

    private fun generateGames() {
        output.text = "Gerando 100 jogos..."
        thread {
            val games = ParetoEngine.generate(draws, 100)
            val file = File(getExternalFilesDir(null), "JOGOS_LOTOFACIL_PARETO.txt")
            file.writeText(games.joinToString("\n") { game ->
                game.numbers.joinToString(" ") { "%02d".format(it) }
            })
            runOnUiThread {
                output.text = buildString {
                    appendLine("100 JOGOS GERADOS")
                    appendLine("Arquivo: ${file.absolutePath}")
                    appendLine()
                    games.take(20).forEach { game ->
                        appendLine(game.numbers.joinToString(" ") { "%02d".format(it) })
                    }
                    if (games.size > 20) appendLine("\n... e mais ${games.size - 20} jogos no TXT.")
                }
            }
        }
    }

    private fun runBlindBacktest() {
        output.text = "Executando BlackTest cego. O concurso testado permanece escondido durante a geração..."
        thread {
            val result = ParetoEngine.blindBacktest(draws, lastN = 100, gamesPerDraw = 100)
            runOnUiThread {
                output.text = buildString {
                    appendLine("BLACKTEST CEGO")
                    appendLine("----------------------------------------")
                    appendLine("Concursos testados: ${result.testedDraws}")
                    appendLine("Jogos por concurso: ${result.gamesPerDraw}")
                    appendLine("11 pontos: ${result.hits11}")
                    appendLine("12 pontos: ${result.hits12}")
                    appendLine("13 pontos: ${result.hits13}")
                    appendLine("14 pontos: ${result.hits14}")
                    appendLine("15 pontos: ${result.hits15}")
                    appendLine("Melhor pontuação: ${result.bestHit}")
                    appendLine()
                    appendLine("Regra cega: para testar o concurso N, o motor usa somente concursos anteriores a N.")
                }
            }
        }
    }

    @Deprecated("Deprecated in Android API, kept for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_TXT || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
        draws = ParetoEngine.parse(text)

        val ready = draws.size >= 10
        analyzeButton.isEnabled = ready
        suggestButton.isEnabled = ready
        generateButton.isEnabled = ready
        backtestButton.isEnabled = ready

        output.text = "Concursos carregados: ${draws.size}\n\nÚltimos 10 concursos prontos para análise P01–P15."
    }
}
