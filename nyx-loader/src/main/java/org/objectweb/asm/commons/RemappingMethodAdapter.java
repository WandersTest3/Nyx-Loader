package org.objectweb.asm.commons;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@Deprecated
public class RemappingMethodAdapter extends MethodRemapper {

    public RemappingMethodAdapter(int access, String descriptor, MethodVisitor visitor, Remapper remapper) {
        super(Opcodes.ASM9, visitor, remapper);
    }

    protected RemappingMethodAdapter(int api, int access, String descriptor, MethodVisitor visitor,
            Remapper remapper) {
        super(api < Opcodes.ASM9 ? Opcodes.ASM9 : api, visitor, remapper);
    }
}
