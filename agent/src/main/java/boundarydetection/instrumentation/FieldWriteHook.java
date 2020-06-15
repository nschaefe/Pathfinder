package boundarydetection.instrumentation;

import javassist.CtClass;
import javassist.bytecode.*;
import javassist.convert.Transformer;

import java.util.function.Predicate;

public class FieldWriteHook extends FieldAccessHook {

    public FieldWriteHook(Transformer next, String methodClassname, Predicate<String> filter) {
        super(next, methodClassname, filter);
    }

    @Override
    public int transform(CtClass tclazz, int pos, CodeIterator iterator,
                         ConstPool cp) throws BadBytecode {
        if (methodInfo.isStaticInitializer()) return pos;

        int c = iterator.byteAt(pos);
        boolean isFieldRead = c == GETFIELD || c == GETSTATIC;
        boolean isFieldWrite = c == PUTFIELD || c == PUTSTATIC;
        boolean isFieldAccess = isFieldRead || isFieldWrite;
        boolean isStatic = c == GETSTATIC || c == PUTSTATIC;

        if (isFieldWrite) {
            int index = iterator.u16bitAt(pos + 1);
            String typedesc = cp.getFieldrefType(index);
            if (typedesc != null && toInstrument(typedesc)) {
                iterator.move(pos);
                if (isStatic) {
                    if (Util.isObjectSignature(typedesc)) {
                        pos = iterator.insertGap(3);
                        iterator.writeByte(Opcode.DUP, pos);
                        iterator.writeByte(ACONST_NULL, pos + 1);
                        iterator.writeByte(Opcode.SWAP, pos + 2);
                        pos += 3;
                        CodeAttribute ca = iterator.get();
                        ca.setMaxStack(ca.getMaxStack() + 2);
                    } else {
                        pos = iterator.insertGap(1);
                        iterator.writeByte(ACONST_NULL, pos);
                        pos += 1;
                        CodeAttribute ca = iterator.get();
                        ca.setMaxStack(ca.getMaxStack() + 1);
                    }
                } else {
                    if (Util.isDoubleOrLong(typedesc)) {
                        pos = iterator.insertGap(3);
                        iterator.writeByte(Opcode.DUP2_X1, pos);
                        iterator.writeByte(Opcode.POP2, pos + 1);
                        iterator.writeByte(Opcode.DUP_X2, pos + 2);
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
                        if (!Util.isObjectSignature(typedesc)) {
                            pos = iterator.insertGap(1);
                            iterator.writeByte(Opcode.POP, pos);
                            pos += 1;
                        }
                    }
                }

                String classname = getFieldRefDeclaringClassName(tclazz, cp, index);
                String fieldname = cp.getFieldrefName(index);
                int str_index = cp.addStringInfo(classname + '.' + fieldname);
                pos = addLdc(str_index, iterator, pos);

                pos = iterator.insertGap(3);

                String mdName;
                String typ;
                if (Util.isSingleObjectSignature(typedesc) || Util.isArraySignature(typedesc)) {
                    typ = "Ljava/lang/Object;";
                    mdName = "writeObject";
                } else {
                    typ = "";
                    mdName = "write" + typedesc;
                }
                String type = "(Ljava/lang/Object;" + typ + "Ljava/lang/String;)V";
                int mi = cp.addClassInfo(methodClassname);
                int methodref = cp.addMethodrefInfo(mi, mdName, type);
                iterator.writeByte(INVOKESTATIC, pos);
                iterator.write16bit(methodref, pos + 1);
                pos += 3;

                pos = iterator.next();
                return pos;
            }
        }
        return pos;
    }


}
