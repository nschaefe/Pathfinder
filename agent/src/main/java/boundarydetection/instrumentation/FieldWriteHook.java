package boundarydetection.instrumentation;

import javassist.CtClass;
import javassist.bytecode.*;
import javassist.convert.Transformer;

public class FieldWriteHook extends FieldAccessHook {

    public FieldWriteHook(Transformer next, String methodClassname, String methodName) {
        super(next, methodClassname, methodName);
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
            String fieldname = cp.getFieldrefName(index);
            String typedesc = cp.getFieldrefType(index);
            if (typedesc != null && Util.isSingleObjectSignature(typedesc)) {

                iterator.move(pos);
                if (isStatic) {
                    pos = iterator.insertGap(3);
                    iterator.writeByte(Opcode.DUP, pos);
                    iterator.writeByte(ACONST_NULL, pos + 1);
                    iterator.writeByte(Opcode.SWAP, pos + 2);
                    pos += 3;
                    CodeAttribute ca = iterator.get();
                    ca.setMaxStack(ca.getMaxStack() + 2);
                } else {
                    pos = iterator.insertGap(4);
                    iterator.writeByte(Opcode.SWAP, pos);
                    iterator.writeByte(Opcode.DUP_X1, pos + 1);
                    iterator.writeByte(Opcode.SWAP, pos + 2);
                    iterator.writeByte(Opcode.DUP_X1, pos + 3);
                    pos += 4;
                    CodeAttribute ca = iterator.get();
                    ca.setMaxStack(ca.getMaxStack() + 2);
                }

                int str_index = cp.addStringInfo(className + '.' + fieldname);
                pos = addLdc(str_index, iterator, pos);
                CodeAttribute ca = iterator.get();
                ca.setMaxStack(ca.getMaxStack() + 1);

                pos = iterator.insertGap(3);
                String type = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V";
                int mi = cp.addClassInfo(methodClassname);
                int methodref = cp.addMethodrefInfo(mi, methodName, type);
                iterator.writeByte(INVOKESTATIC, pos);
                iterator.write16bit(methodref, pos + 1);
                pos += 3;

                pos = iterator.next();
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
