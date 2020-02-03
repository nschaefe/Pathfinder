package boundarydetection.instrumentation;

import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.*;
import javassist.convert.TransformWriteField;
import javassist.convert.Transformer;

public class FieldWriteHook extends FieldAccessHook {

    public FieldWriteHook(Transformer next, CtField field, String methodClassname, String methodName) {
        super(next, field, methodClassname, methodName);
    }

    private boolean skippedConstCall = false;

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

        if (isFieldWrite) {
            int index = iterator.u16bitAt(pos + 1);
            String typedesc = isField(tclazz.getClassPool(), cp,
                    fieldClass, fieldname, isPrivate, index);
            if (typedesc != null && Util.isSingleObjectSignature(typedesc)) {

                iterator.move(pos);
                if (isStatic) {
                    pos = iterator.insertGap(1);
                    iterator.writeByte(ACONST_NULL, pos);
                    pos += 1;
                    CodeAttribute ca = iterator.get();
                    ca.setMaxStack(ca.getMaxStack() + 1);
                } else {
                    // putfield takes a reference, this reference must be passed to our tracker method, copy this ref:
                    // swap ref, value -> value, ref ; dup ref on second level -> ref, value, ref ; swap again -> ref, ref, value
                    pos = iterator.insertGap(2);
                    iterator.writeByte(Opcode.SWAP, pos);
                    iterator.writeByte(Opcode.DUP, pos+1);
                    pos+=2;
                    CodeAttribute ca = iterator.get();
                    ca.setMaxStack(ca.getMaxStack() + 1);
                }

                int str_index = cp.addStringInfo(fieldClass.getName() + '.' + fieldname);
                pos = addLdc(str_index, iterator, pos);
                CodeAttribute ca = iterator.get();
                ca.setMaxStack(ca.getMaxStack() + 1);

                pos = iterator.insertGap(3);
                String type = "(Ljava/lang/Object;Ljava/lang/String;)V";
                int mi = cp.addClassInfo(methodClassname);
                int methodref = cp.addMethodrefInfo(mi, methodName, type);
                iterator.writeByte(INVOKESTATIC, pos);
                iterator.write16bit(methodref, pos + 1);
                pos+=3;

                if (!isStatic) {
                    pos = iterator.insertGap(1);
                    iterator.writeByte(Opcode.SWAP, pos);
                    pos+=1;
                }

                iterator.next();
                return pos;
            }
        }
        return pos;
    }


//
//    public int transform(CtClass tclazz, int pos, CodeIterator iterator, ConstPool cp) throws BadBytecode {
//
//        int c = iterator.byteAt(pos);
//        if (c == PUTFIELD || c == PUTSTATIC) {
//            int index = iterator.u16bitAt(pos + 1);
//            String typedesc = isField(tclazz.getClassPool(), cp,
//                    fieldClass, fieldname, isPrivate, index);
//            if (typedesc != null && Util.isSingleObjectSignature(typedesc)) {
//                if (c == PUTSTATIC) {
//                    CodeAttribute ca = iterator.get();
//                    iterator.move(pos);
//                    char c0 = typedesc.charAt(0);
//                    if (c0 == 'J' || c0 == 'D') {       // long or double
//                        // insertGap() may insert 4 bytes.
//                        pos = iterator.insertGap(3);
//                        iterator.writeByte(ACONST_NULL, pos);
//                        iterator.writeByte(DUP_X2, pos + 1);
//                        iterator.writeByte(POP, pos + 2);
//                        ca.setMaxStack(ca.getMaxStack() + 2);
//                    } else {
//                        // insertGap() may insert 4 bytes.
//                        pos = iterator.insertGap(2);
//                        iterator.writeByte(ACONST_NULL, pos);
//                        iterator.writeByte(SWAP, pos + 1);
//                        ca.setMaxStack(ca.getMaxStack() + 1);
//                    }
//
//                    pos = iterator.next();
//                }
//
//                int mi = cp.addClassInfo(methodClassname);
//                String type = "(Ljava/lang/Object;" + typedesc + ")V";
//                int methodref = cp.addMethodrefInfo(mi, methodName, type);
//                iterator.writeByte(INVOKESTATIC, pos);
//                iterator.write16bit(methodref, pos + 1);
//            }
//        }
//
//        return pos;
//    }
}
