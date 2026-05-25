package me.lucaperri.dev.languages.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.nextLeaf
import com.intellij.psi.util.prevLeaf
import me.lucaperri.dev.languages.psi.NasmNamedElement

class NasmDocumentationProvider : AbstractDocumentationProvider() {

    // When hovering over a known instruction or register, return the leaf directly
    // as the documentation target. Without this, DocumentationManager tries to
    // resolve the reference at cursor: registers (NasmLabelRef) resolve to null,
    // mnemonics (raw IDENTIFIER) have no reference at all — in both cases
    // generateDoc may never be called.
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        val text = contextElement?.text?.lowercase() ?: return null
        if (text in NasmInstructionDocs.MAP || text in NasmRegisterDocs.MAP) return contextElement
        return null
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element is NasmNamedElement) return labelDoc(element)
        val text = (originalElement?.text ?: element?.text)?.lowercase() ?: return null
        NasmInstructionDocs.MAP[text]?.let { desc ->
            val flags    = if (text in NasmInstructionMeta.LOCKABLE) listOf("lockable" to "#0068CF") else emptyList()
            val sections = NasmInstructionSections.MAP[text] ?: emptyList()
            return doc("Instruction", text, desc, NasmInstructionsShape.MAP[text] ?: "", flags, sections)
        }
        NasmRegisterDocs.MAP[text]?.let { desc ->
            val flags = buildList {
                NasmRegisterVolatility.MAP[text]?.let { vol ->
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
                if (text in NasmRegisterMeta.READONLY) add("read-only" to "#CC0000")
            }
            val sections = NasmRegisterSections.MAP[text] ?: emptyList()
            return doc("Register", text, desc, "", flags, sections)
        }
        return null
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val text = (originalElement?.text ?: element?.text)?.lowercase() ?: return null
        return NasmInstructionDocs.MAP[text]
            ?: NasmRegisterDocs.MAP[text]
            ?: (element as? NasmNamedElement)?.let { "label ${it.name}" }
    }

    private fun PsiElement.lineNumber(): Int =
        containingFile?.viewProvider?.document?.getLineNumber(textRange.startOffset) ?: -1

    private val tagRegex = Regex("""^(Input|Output|Flags|Clobbers)\s*:(.*)""", RegexOption.IGNORE_CASE)

    private fun parseCommentDoc(lines: List<String>): Pair<String, List<Pair<String, String>>> {
        val descLines = mutableListOf<String>()
        val sections = mutableListOf<Pair<String, MutableList<String>>>()

        for (line in lines) {
            val match = tagRegex.matchEntire(line)
            when {
                match != null -> {
                    val tag = match.groupValues[1].replaceFirstChar { it.uppercase() }
                    val rest = match.groupValues[2].trim()
                val content = mutableListOf<String>()
                    if (rest.isNotEmpty()) content.add(rest)
                    sections.add(tag to content)
                }
                sections.isNotEmpty() -> if (line.isNotEmpty()) sections.last().second.add(line)
                else -> descLines.add(line)
            }
        }

        val order = listOf("Input", "Output", "Flags", "Clobbers")
        val description = descLines.filter { it.isNotEmpty() }.joinToString("<br>")
        val result = sections
            .sortedBy { (tag, _) -> order.indexOf(tag).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .map { (tag, tagLines) -> tag to tagLines.joinToString("<br>") }
        return description to result
    }

    private fun labelDoc(label: NasmNamedElement): String = buildString {
        val rawComments = mutableListOf<PsiElement>()
        var comment = label.prevLeaf { it.text.trimStart().startsWith(";") && it.lineNumber() + 1 == label.lineNumber() }
        while (comment != null) {
            rawComments.add(comment)
            val cur = comment
            comment = cur.prevLeaf { it.text.trimStart().startsWith(";") && it.lineNumber() + 1 == cur.lineNumber() } ?: break
        }

        append(DocumentationMarkup.DEFINITION_START)
        append("label <b>").append(label.name ?: "<anonymous>").append("</b>")
        append(DocumentationMarkup.DEFINITION_END)

        if (rawComments.isNotEmpty()) {
            val lines = rawComments.reversed().map { it.text.trimStart().removePrefix(";").trim() }
            val (description, sections) = parseCommentDoc(lines)

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
