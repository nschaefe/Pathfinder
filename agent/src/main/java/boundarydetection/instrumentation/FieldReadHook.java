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

    public FieldReadHook(Transformer next, String methodClassname, String methodName) {
        super(next, methodClassname, methodName);
    }

    @Override
    public void clean() {
        skippedConstCall = false;
    }

    @Override
    public int transform(CtClass tclazz, int pos, CodeIterator iterator,
                         ConstPool cp) throws BadBytecode {
        //(methodInfo.getAccessFlags() & AccessFlag.ABSTRACT) != 0
        if (methodInfo.isStaticInitializer()) return pos;
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

        //REMARK: It would be nicer to call a tracking method with the parent and the field object itself.
        // I could not realize this because of the following reason: I tried to duplicate the parent of a field before
        // get field is executed. For some reason the duplicate is not on the stack anymore after the execution of getfield
        // so a static call after the getfield that consumes the parent, the field and the location string, actually consumed
        // the duplicated field and the field that should remain on the stack for following instructions.

        if (isFieldRead) {
            int index = iterator.u16bitAt(pos + 1);

            String typedesc = cp.getFieldrefType(index);
            if (typedesc != null && (Util.isSingleObjectSignature(typedesc) || Util.isObjectArraySignature(typedesc))) {

                String mdName = methodName;
                if (Util.isSingleObjectSignature(typedesc)) {
                    iterator.move(pos);
                    pos = iterator.insertGap(1);
                    if (isStatic) iterator.writeByte(ACONST_NULL, pos);
                    else iterator.writeByte(Opcode.DUP, pos);
                    pos += 1;
                } else if (Util.isObjectArraySignature(typedesc)) {
                    mdName += "ArrayField";
                    pos = iterator.insertGap(1);
                    iterator.writeByte(Opcode.DUP, pos);
                    pos += 1;
                }

                String classname = getFieldRefDeclaringClassName(tclazz, cp, index);
                String fieldname = cp.getFieldrefName(index);
                int str_index = cp.addStringInfo(classname + '.' + fieldname);
                pos = addLdc(str_index, iterator, pos);

                pos = iterator.insertGap(3);
                String type = "(Ljava/lang/Object;Ljava/lang/String;)V";
                int mi = cp.addClassInfo(methodClassname);
                int methodref = cp.addMethodrefInfo(mi, mdName, type);
                iterator.writeByte(INVOKESTATIC, pos);
                iterator.write16bit(methodref, pos + 1);
                pos += 3;

                CodeAttribute ca = iterator.get();
                ca.setMaxStack(ca.getMaxStack() + 2);
                if (Util.isSingleObjectSignature(typedesc)) pos = iterator.next();
            }
            return pos;
        }
        return pos;
    }

}
