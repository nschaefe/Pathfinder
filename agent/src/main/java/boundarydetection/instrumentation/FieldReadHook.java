package boundarydetection.instrumentation;

/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

import javassist.*;
import javassist.bytecode.*;
import javassist.convert.Transformer;

public class FieldReadHook extends FieldAccessHook {

    private boolean skippedConstCall = false;

    public FieldReadHook(Transformer next, CtField field, String methodClassname, String methodName) {
        super(next, field, methodClassname, methodName);
    }

    @Override
    public void clean() {
        skippedConstCall = false;
    }

    @Override
    public int transform(CtClass tclazz, int pos, CodeIterator iterator,
                         ConstPool cp) throws BadBytecode {
        // jump over all instructions before super or this.
        // static field accesses can happen before super,
        // so not doing this can lead to a method call injection before super or this,
        // what leads to passing uninitializedThis to method call, what is not allowed
        if (methodInfo.isConstructor() && !skippedConstCall) {
            int onCallIndex = iterator.skipConstructor();
            if (onCallIndex != -1) {
                //jumping over super or this call, pos is after
                iterator.move(onCallIndex);
                pos = iterator.next();
            }
            skippedConstCall = true;
        }


        int c = iterator.byteAt(pos);
        boolean isFieldRead = c == GETFIELD || c == GETSTATIC;
        boolean isFieldWrite = c == PUTFIELD || c == PUTSTATIC;
        boolean isFieldAccess = isFieldRead || isFieldWrite;
        boolean isStatic = c == GETSTATIC || c == PUTSTATIC;

        if (isFieldRead) {
            int index = iterator.u16bitAt(pos + 1);
            String typedesc = isField(tclazz.getClassPool(), cp,
                    fieldClass, fieldname, isPrivate, index);
            if (typedesc != null && Util.isSingleObjectSignature(typedesc)) {

                pos = iterator.insertGap(1);
                if (isStatic) iterator.writeByte(ACONST_NULL, pos);
                else iterator.writeByte(Opcode.ALOAD_0, pos);
                pos += 1;

                int str_index = cp.addStringInfo(fieldClass.getName()+'.'+fieldname);
                pos = addLdc(str_index, iterator, pos);

                pos = iterator.insertGap(3);
                String type = "(Ljava/lang/Object;Ljava/lang/String;)V";
                int mi = cp.addClassInfo(methodClassname);
                int methodref = cp.addMethodrefInfo(mi, methodName, type);
                iterator.writeByte(INVOKESTATIC, pos);
                iterator.write16bit(methodref, pos + 1);

                CodeAttribute ca = iterator.get();
                ca.setMaxStack(ca.getMaxStack() + 2);

                return pos;
            }
        }
        return pos;
    }
}