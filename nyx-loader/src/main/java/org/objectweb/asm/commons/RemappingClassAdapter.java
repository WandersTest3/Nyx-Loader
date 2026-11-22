package org.objectweb.asm.commons;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@Deprecated
public class RemappingClassAdapter extends ClassRemapper {

    public RemappingClassAdapter(ClassVisitor classVisitor, Remapper remapper) {
        super(Opcodes.ASM9, classVisitor, remapper);
    }

    protected RemappingClassAdapter(int api, ClassVisitor classVisitor, Remapper remapper) {
        super(api < Opcodes.ASM9 ? Opcodes.ASM9 : api, classVisitor, remapper);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
            String[] exceptions) {
        String newDescriptor = remapper.mapMethodDesc(descriptor);
        MethodVisitor visitor = cv.visitMethod(access, remapper.mapMethodName(className, name, descriptor),
                newDescriptor, remapper.mapSignature(signature, false),
                exceptions == null ? null : remapper.mapTypes(exceptions));
        return visitor == null ? null : createRemappingMethodAdapter(access, newDescriptor, visitor);
    }

    protected MethodVisitor createRemappingMethodAdapter(int access, String newDescriptor, MethodVisitor visitor) {
        return new RemappingMethodAdapter(access, newDescriptor, visitor, remapper);
    }
}
