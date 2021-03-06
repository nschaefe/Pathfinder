package boundarydetection.instrumentation;


import javassist.CtClass;
import javassist.bytecode.*;
import javassist.convert.Transformer;

public class ArrayFieldValueReadHook extends FieldAccessHook {

    public ArrayFieldValueReadHook(Transformer next, String methodClassname) {
        super(next, methodClassname, null);
    }

    @Override
    public int transform(CtClass tclazz, int pos, CodeIterator iterator,
                         ConstPool cp) throws BadBytecode {
        //(methodInfo.getAccessFlags() & AccessFlag.ABSTRACT) != 0
        if (methodInfo.isStaticInitializer()) return pos;

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
            if (typedesc != null && Util.isArraySignature(typedesc)) {

                String mdName;
                mdName = "readArrayField";
                pos = iterator.insertGap(1);
                iterator.writeByte(Opcode.DUP, pos);
                pos += 1;
                CodeAttribute ca = iterator.get();
                ca.setMaxStack(ca.getMaxStack() + 1);

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

            }
            return pos;
        }
        return pos;
    }
}
