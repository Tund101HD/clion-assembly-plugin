package me.lucaperri.dev.languages.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import me.lucaperri.dev.languages.psi.MipsTypes;
import com.intellij.psi.TokenType;

%%

%class MipsLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

EOL              = \r|\n|\r\n
WHITE_SPACE      = [ \t]+
LINE_COMMENT     = #[^\r\n]*

// MIPS identifiers cover:
//   - regular names: foo, _bar, main
//   - $-prefixed register names: $t0, $sp, $a0, $zero
//   - $-prefixed numeric registers: $0, $1, ..., $31
//   - floating-point register names: $f0..$f31 (already covered by the alpha form)
//   - mnemonics with float-format suffix: add.s, mul.d, cvt.w.s (one or two trailing .X groups)
// A leading `.` without alphanumeric content remains a DIRECTIVE (.text, .data).
IDENTIFIER       = (\$[a-zA-Z_][a-zA-Z0-9_]*) | (\$[0-9]+) | ([a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z]+)*)

HEX_NUMBER       = 0[xX][0-9a-fA-F]+
DEC_NUMBER       = [0-9]+
NUMBER           = {HEX_NUMBER}|{DEC_NUMBER}

STRING           = \"([^\"\r\n\\]|\\.)*\"

// MIPS directives are dot-prefixed: .text, .data, .globl, .word, .asciiz, ...
DIRECTIVE        = \.[a-zA-Z_][a-zA-Z0-9_]*

%%

{WHITE_SPACE}                       { return TokenType.WHITE_SPACE; }
{EOL}                               { return MipsTypes.EOL; }
{LINE_COMMENT}                      { return MipsTypes.COMMENT; }
{DIRECTIVE}                         { return MipsTypes.DIRECTIVE; }
{NUMBER}                            { return MipsTypes.NUMBER; }
{STRING}                            { return MipsTypes.STRING; }
{IDENTIFIER}                        { return MipsTypes.IDENTIFIER; }
":"                                 { return MipsTypes.COLON; }
","                                 { return MipsTypes.COMMA; }
"("                                 { return MipsTypes.LPAREN; }
")"                                 { return MipsTypes.RPAREN; }
"+"                                 { return MipsTypes.PLUS; }
"-"                                 { return MipsTypes.MINUS; }
"="                                 { return MipsTypes.EQUALS; }
// `.` alone (location counter) — longest-match makes `.text` still hit DIRECTIVE.
"."                                 { return MipsTypes.DOT; }

[^]                                 { return TokenType.BAD_CHARACTER; }
