/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.org.objectweb.asm;

import moe.yushi.authlibinjector.internal.org.objectweb.asm.Frame;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Label;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Symbol;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.SymbolTable;

final class CurrentFrame
extends Frame {
    CurrentFrame(Label owner) {
        super(owner);
    }

    void execute(int opcode, int arg, Symbol symbolArg, SymbolTable symbolTable) {
        super.execute(opcode, arg, symbolArg, symbolTable);
        Frame successor = new Frame(null);
        this.merge(symbolTable, successor, 0);
        this.copyFrom(successor);
    }
}

