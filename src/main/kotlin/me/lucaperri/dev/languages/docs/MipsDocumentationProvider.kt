package me.lucaperri.dev.languages.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.prevLeaf
import me.lucaperri.dev.languages.psi.MipsNamedElement

class MipsDocumentationProvider : AbstractDocumentationProvider() {

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        val text = contextElement?.text?.lowercase() ?: return null
        if (text in MipsInstructionDocs.MAP || text in MipsRegisterDocs.MAP) return contextElement
        return null
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is MipsNamedElement) return labelDoc(element)
        val text = (originalElement?.text ?: element?.text)?.lowercase() ?: return null
        MipsInstructionDocs.MAP[text]?.let { desc ->
            val sections = MipsInstructionSections.MAP[text] ?: emptyList()
            return doc("Instruction", text, desc, MipsInstructionsShape.MAP[text] ?: "", emptyList(), sections)
        }
        MipsRegisterDocs.MAP[text]?.let { desc ->
            val flags = buildList {
                MipsRegisterVolatility.MAP[text]?.let { vol ->
                    when (vol) {
                        "volatile" -> {
                            add("volatile"     to "#E8740A")
                            add("caller-saved" to "#E8740A")
                        }
                        "non-volatile" -> {
                            add("non-volatile" to "#178600")
                            add("callee-saved" to "#178600")
                        }
                        else -> add("special" to "#888888")
                    }
                }
                if (text in MipsRegisterMeta.READONLY) add("read-only" to "#CC0000")
            }
            val sections = MipsRegisterSections.MAP[text] ?: emptyList()
            return doc("Register", text, desc, "", flags, sections)
        }
        return null
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val text = (originalElement?.text ?: element?.text)?.lowercase() ?: return null
        return MipsInstructionDocs.MAP[text]
            ?: MipsRegisterDocs.MAP[text]
            ?: (element as? MipsNamedElement)?.let { "label ${it.name}" }
    }

    private fun PsiElement.lineNumber(): Int =
        containingFile?.viewProvider?.document?.getLineNumber(textRange.startOffset) ?: -1

    private val mipsTagRegex = Regex("""^(\w+)\s*:(.*)""", RegexOption.IGNORE_CASE)

    // rawLines: each line has the leading `#` removed but is NOT trimmed, so
    // indentation is still intact for continuation detection.
    private fun parseMipsCommentDoc(rawLines: List<String>): Pair<String, List<Pair<String, String>>> {
        val descLines = mutableListOf<String>()
        val sections = mutableListOf<Pair<String, MutableList<String>>>()

        for (raw in rawLines) {
            val line = raw.trim()
            if (line.isEmpty() || line.all { it == '=' || it == '-' || it == '*' || it == '#' }) continue

            if (raw.startsWith("  ")) {
                // indented continuation — belongs to the last open section
                if (sections.isNotEmpty()) sections.last().second.add(line)
                continue
            }

            val match = mipsTagRegex.matchEntire(line)
            if (match != null) {
                val tag = match.groupValues[1]
                val rest = match.groupValues[2].trim()
                when (tag.lowercase()) {
                    "function" -> continue
                    "purpose" -> if (rest.isNotEmpty()) descLines.add(rest)
                    else -> {
                        val content = mutableListOf<String>()
                        if (rest.isNotEmpty()) content.add(rest)
                        sections.add(tag to content)
                    }
                }
            } else {
                descLines.add(line)
            }
        }

        val description = descLines.joinToString("<br>")
        val result = sections.map { (tag, tagLines) -> tag to tagLines.joinToString("<br>") }
        return description to result
    }

    private fun labelDoc(label: MipsNamedElement): String = buildString {
        val rawComments = mutableListOf<PsiElement>()
        var comment = label.prevLeaf { it.text.trimStart().startsWith("#") && it.lineNumber() + 1 == label.lineNumber() }
        while (comment != null) {
            rawComments.add(comment)
            val cur = comment
            comment = cur.prevLeaf { it.text.trimStart().startsWith("#") && it.lineNumber() + 1 == cur.lineNumber() } ?: break
        }

        append(DocumentationMarkup.DEFINITION_START)
        append("label <b>").append(label.name ?: "<anonymous>").append("</b>")
        append(DocumentationMarkup.DEFINITION_END)

        if (rawComments.isNotEmpty()) {
            // Pass raw (untrimmed) lines so the parser can detect indentation.
            val lines = rawComments.reversed().map { it.text.trimStart().removePrefix("#") }
            val (description, sections) = parseMipsCommentDoc(lines)

            if (description.isNotEmpty()) {
                append(DocumentationMarkup.CONTENT_START)
                append(description)
                append(DocumentationMarkup.CONTENT_END)
            }
            if (sections.isNotEmpty()) {
                append(DocumentationMarkup.SECTIONS_START)
                for ((header, content) in sections) {
                    append(DocumentationMarkup.SECTION_HEADER_START).append(header).append(":")
                    append(DocumentationMarkup.SECTION_SEPARATOR).append(" ").append(content)
                    append(DocumentationMarkup.SECTION_END)
                }
                append(DocumentationMarkup.SECTIONS_END)
            }
        }

        append(DocumentationMarkup.SECTIONS_START)
        append(DocumentationMarkup.SECTION_HEADER_START).append("Defined in:")
        append(DocumentationMarkup.SECTION_SEPARATOR).append(" ").append(label.containingFile?.name ?: "this file")
        append(DocumentationMarkup.SECTION_END)
        append(DocumentationMarkup.SECTIONS_END)
    }

    private fun doc(
        kind: String,
        name: String,
        body: String,
        subtext: String,
        flags: List<Pair<String, String>> = emptyList(),
        sections: List<Pair<String, String>> = emptyList()
    ): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append(kind).append(" <b>").append(name).append("</b>")
        for ((label, color) in flags) {
            append("&nbsp;<small><span style=\"color: $color; font-weight: bold;\">[$label]</span></small>")
        }
        if (subtext.isNotEmpty()) append("<br><small><code>").append(subtext).append("</code></small>")
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        append(body)
        append(DocumentationMarkup.CONTENT_END)
        if (sections.isNotEmpty()) {
            append(DocumentationMarkup.SECTIONS_START)
            for ((header, content) in sections) {
                append(DocumentationMarkup.SECTION_HEADER_START)
                append(header).append(":")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append(" ").append(content)
                append(DocumentationMarkup.SECTION_END)
            }
            append(DocumentationMarkup.SECTIONS_END)
        }
    }
}
