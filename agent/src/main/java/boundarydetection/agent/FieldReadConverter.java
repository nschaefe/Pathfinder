package boundarydetection.agent;

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
import javassist.convert.TransformReadField;
import javassist.convert.Transformer;

public class FieldReadConverter extends TransformReadField {

    private MethodInfo methodInfo;
    private boolean skippedConstCall = false;

    public FieldReadConverter(Transformer next, CtField field,
                              String methodClassname, String methodName) {
        super(next, field, methodClassname, methodName);
    }

    @Override
    public void initialize(ConstPool cp, CtClass clazz, MethodInfo minfo) throws CannotCompileException {
        methodInfo = minfo;
        initialize(cp, minfo.getCodeAttribute());
    }

    static String isField(ClassPool pool, ConstPool cp, CtClass fclass,
                          String fname, boolean is_private, int index) {
        if (!cp.getFieldrefName(index).equals(fname))
            return null;

        try {
            CtClass c = pool.get(cp.getFieldrefClassName(index));
            if (c == fclass || (!is_private && isFieldInSuper(c, fclass, fname)))
                return cp.getFieldrefType(index);
        } catch (NotFoundException e) {
        }
        return null;
    }

    static boolean isFieldInSuper(CtClass clazz, CtClass fclass, String fname) {
        if (!clazz.subclassOf(fclass))
            return false;

        try {
            CtField f = clazz.getField(fname);
            return f.getDeclaringClass() == fclass;
        } catch (NotFoundException e) {
        }
        return false;
    }

    private int addLdc(int i, CodeIterator iterator, int pos) throws BadBytecode {
        if (i > 0xFF) {
            pos = iterator.insertGap(3);
            iterator.writeByte(LDC_W, pos);
            iterator.write16bit(i, pos + 1);
            pos += 3;

        } else {
            pos = iterator.insertGap(2);
            iterator.writeByte(LDC, pos);
            iterator.writeByte(i, pos + 1);
            pos += 2;
        }
        return pos;
    }


    private boolean isObjectSig(String s) {
        if (s.startsWith("[")) return false;
        return s.length() != 1;//TODO
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
        if (c == GETFIELD || c == GETSTATIC) {
            int index = iterator.u16bitAt(pos + 1);
            String typedesc = isField(tclazz.getClassPool(), cp,
                    fieldClass, fieldname, isPrivate, index);
            if (typedesc != null && isObjectSig(typedesc)) {

                pos = iterator.insertGap(1);
                if (c == GETSTATIC) iterator.writeByte(ACONST_NULL, pos);
                else iterator.writeByte(Opcode.ALOAD_0, pos);
                pos += 1;

                int str_index = cp.addStringInfo("infoString");
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
