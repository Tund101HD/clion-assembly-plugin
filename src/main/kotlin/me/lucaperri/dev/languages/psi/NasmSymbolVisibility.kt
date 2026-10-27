package me.lucaperri.dev.languages.psi

import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.NasmFileType

/**
 * A non-local symbol defined in a NASM file, plus whether that file exports it
 * with `global`. Only exported symbols are visible to other object files at link
 * time; [NasmLabelReference] and the extern inspection use this to tell the two apart.
 */
class NasmSymbolDef(val element: NasmNamedElement, val file: NasmFile, val isGlobal: Boolean)

/** Names listed in this file's `global` statements. */
fun NasmFile.globalNames(): Set<String> =
    CachedValuesManager.getCachedValue(this) {
        val names = PsiTreeUtil.findChildrenOfType(this, NasmGlobalStmt::class.java)
            .flatMap { it.labelRefList }
            .mapNotNull { it.node.findChildByType(NasmTypes.IDENTIFIER)?.text }
            .toSet()
        CachedValueProvider.Result.create(names, this)
    }

/** The first non-local definition of [name] in this file, if any. */
fun NasmFile.findNonLocalDefinition(name: String): NasmNamedElement? =
    PsiTreeUtil.findChildrenOfType(this, NasmNamedElement::class.java)
        .firstOrNull { it.name == name && !name.isNasmLocalLabel() }

/**
 * Every non-local definition of [name] in NASM files other than [origin], exported
 * (`global`) definitions first.
 */
fun foreignNasmDefinitions(origin: NasmFile, name: String): List<NasmSymbolDef> {
    val manager = PsiManager.getInstance(origin.project)
    // originalFile: during completion [origin] is a non-physical copy of the editor file.
    val originVf = origin.originalFile.virtualFile
    return FileTypeIndex.getFiles(NasmFileType.INSTANCE, GlobalSearchScope.allScope(origin.project))
        .filter { it != originVf }
        .mapNotNull { manager.findFile(it) as? NasmFile }
        .mapNotNull { file ->
            file.findNonLocalDefinition(name)?.let { NasmSymbolDef(it, file, name in file.globalNames()) }
        }
        .sortedByDescending { it.isGlobal }
}
