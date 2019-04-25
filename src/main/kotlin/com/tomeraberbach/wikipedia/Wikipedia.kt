package com.tomeraberbach.wikipedia

import edu.stanford.nlp.simple.Document
import it.unimi.dsi.fastutil.objects.*
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parseMap
import java.io.File
import java.text.Normalizer


/**
 * Class representing Wikipedia articles. [directory] is expected to be a directory
 * which is the JSON output of [WikiExtractor](https://github.com/attardi/wikiextractor).
 */
@ImplicitReflectionSerializer
class Wikipedia(private val directory: File) {
    companion object {
        /**
         * A [Map] from left delimiters to right delimiters which create contexts.
         */
        val DELIMITERS: Map<String, String> = mapOf("-LRB-" to "-RRB-", "[" to "]", "``" to "''")
    }

    /**
     * A [Sequence] of all files in [directory] recursively.
     */
    private val files: Sequence<File> get() = directory.walkTopDown().filter { it.isFile }

    /**
     * A [Sequence] of all article texts in [directory].
     */
    private val texts: Sequence<String>
        get() = files
            .flatMap { it.readLines().asSequence() }
            .mapNotNull { Json.parseMap<String, String>(it)["text"] }

    /**
     * A [Sequence] of all paragraphs in [directory].
     */
    private val paragraphs: Sequence<String> get() = texts.flatMap { it.splitToSequence("\n\n") }

    /**
     * A [Sequence] of all sentences in [directory].
     */
    private val sentences: Sequence<List<String>>
        get() = paragraphs
            .flatMap { Document(it).sentences().asSequence() }
            .map { it.words().filterNotNull() }

    /**
     * A [Sequence] of all contexts in [directory].
     */
    val contexts: Sequence<List<String>>
        get() = sentences
            .map { sentence ->
                sentence.asSequence()
                    .map { it.normalize() }
                    .splitPunctuation()
                    .mergeContractions()
            }
            .flatMap { sentence -> sentence.contexts() }
            .flatMap { sentence -> sentence.asSequence().splitByNumbers() }
            .map { sentence ->
                sentence
                    .map { it.toUpperCase().filter { c -> c in 'A'..'Z' } }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
            .filter { it.isNotEmpty() }

    /**
     * Returns this [Sequence] representing a sentence/context with dangling
     * contraction merged with the previous token.
     */
    private fun Sequence<String>.mergeContractions(): Sequence<String> = this
        .windowed(2)
        .flatMap { (first, second) ->
            when {
                second.startsWith('\'') -> sequenceOf("$first$second")
                first.startsWith('\'') -> emptySequence()
                else -> sequenceOf(first)
            }
        }

    /**
     * Returns this token normalized.
     */
    private fun String.normalize(): String = Normalizer.normalize(this, Normalizer.Form.NFKD)

    /**
     * Returns this [Sequence] representing a sentence/context with tokens containing
     * certain punctuation split into multiple tokens.
     */
    private fun Sequence<String>.splitPunctuation(): Sequence<String> = this
        .flatMap {
            if (it.endsWith("''"))
                sequenceOf(it.substring(0, it.length - 2), "''")
            else
                sequenceOf(it)
        }

    /**
     * Returns this [Sequence] representing a sentence/context split into multiple
     * contexts based on [DELIMITERS].
     */
    private fun Sequence<String>.contexts(): Sequence<List<String>> {
        val sentences: MutableList<List<String>> = mutableListOf()
        val stack: MutableList<List<String>> = mutableListOf(toList())

        do {
            val iterator: Iterator<String> = stack.removeAt(stack.lastIndex).iterator()
            val sentence: MutableList<String> = mutableListOf()

            while (iterator.hasNext()) {
                val token: String = iterator.next()

                if (DELIMITERS.containsKey(token)) {
                    val subsentence: MutableList<String> = mutableListOf()
                    var next = ""

                    while (iterator.hasNext() && iterator.next().also { next = it } != DELIMITERS.getValue(token)) {
                        subsentence.add(next)
                    }

                    if (subsentence.isNotEmpty()) {
                        stack.add(subsentence)
                    }
                } else {
                    sentence.add(token)
                }
            }

            sentences.add(sentence)
        } while (stack.isNotEmpty())

        return sentences.asSequence()
    }

    /**
     * Returns this [Sequence] representing a sentence/context split into multiple
     * contexts by numeric tokens.
     */
    private fun Sequence<String>.splitByNumbers(): Sequence<List<String>> {
        val sentences: MutableList<List<String>> = mutableListOf()
        val stack: MutableList<List<String>> = mutableListOf(toList())

        do {
            val sentence: List<String> = stack.removeAt(stack.lastIndex)
            val index: Int = sentence.indexOfFirst { it.any { c -> c in '0'..'9' } }

            sentences.add(
                if (index == -1) {
                    sentence
                } else {
                    stack.add(sentence.subList(index + 1, sentence.size))
                    sentence.subList(0, index)
                }
            )
        } while (stack.isNotEmpty())

        return sentences.asSequence()
    }

    /**
     * Returns ngrams of size [n] in [directory].
     */
    private fun ngrams(n: Int, contexts: Sequence<List<String>> = this.contexts): Sequence<List<String>> =
        contexts.flatMap { context -> context.asSequence().windowed(n) }

    /**
     * Returns the number of times each ngram of size [n] shows up in [directory].
     */
    fun ngramCounts(n: Int, contexts: Sequence<List<String>> = this.contexts): Object2IntOpenHashMap<String> {
        val counts: Object2IntOpenHashMap<String> = Object2IntOpenHashMap()

        ngrams(n, contexts).map { it.joinToString(" ") }.forEach { ngram ->
            counts.addTo(ngram, 1)
        }

        return counts
    }
}

/**
 * Outputs this [Map] to [file] in two columns where the first column contains keys
 * and the second contains values. The entries will be sorted by entry value in descending order.
 */
fun Object2IntOpenHashMap<String>.output(file: File) = file.printWriter().use { writer ->
    Object2IntMaps.fastForEach(this) { (key, value) -> writer.println("$value $key") }
}

@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: java -jar wikipedia.jar <ngram-number> <wikipedia-dir-or-contexts.txt>")
        return
    }

    val n: Int? = args.first().toIntOrNull()
    if (n == null || n <= 0) {
        println("Usage: java -jar wikipedia.jar <ngram-number> <wikipedia-dir-or-contexts.txt>")
        return
    }

    File("out").mkdirs()
    var file = File(args.component2())

    if (file.isDirectory) {
        val wikipedia = Wikipedia(file)
        file = File("out/contexts.txt")
        file.printWriter().use { writer ->
            wikipedia.contexts
                .map { it.joinToString(" ") }
                .forEach { writer.println(it) }
        }
    }

    file.useLines { lines ->
        Wikipedia(file).ngramCounts(n, lines.map { it.split(' ') })
    }.output(File("out/$n-grams.txt"))
}
