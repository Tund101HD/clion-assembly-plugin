package me.lucaperri.dev.languages.psi

import com.intellij.psi.util.PsiTreeUtil

/**
 * NASM local labels start with a single leading dot (`.loop`). Names that begin
 * with `..` are NASM special symbols (`..@`, `..start`) and are NOT local labels.
 */
fun String.isNasmLocalLabel(): Boolean = startsWith(".") && !startsWith("..")

/**
 * The non-local [NasmLabelDef] that owns the local-label scope containing the
 * element at [offset], or `null` when [offset] precedes the first non-local label.
 *
 * A NASM local label (`.loop`) belongs to the most recent non-local `foo:` label
 * above it, so two `.loop` definitions under different parents are distinct
 * symbols — each resolves only within its own scope, and they must not be treated
 * as duplicates. Reference resolution and the duplicate-label inspection share this
 * anchor so they agree on what "the same local label" means.
 */
fun nasmLocalScopeAnchor(file: NasmFile, offset: Int): NasmLabelDef? =
    PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java)
        .filter { it.textRange.startOffset < offset && it.name?.isNasmLocalLabel() == false }
        .maxByOrNull { it.textRange.startOffset }
