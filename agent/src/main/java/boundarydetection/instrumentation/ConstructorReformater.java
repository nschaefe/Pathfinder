package boundarydetection.instrumentation;

import javassist.CtClass;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
import javassist.convert.Transformer;

public class ConstructorReformater extends TransformerBase {


    private boolean skippedConstCall = false;

    public ConstructorReformater(Transformer next) {
        super(next);
    }

    @Override
    public void clean() {
        skippedConstCall = false;
    }

    private boolean isObjectSuper(String clName, String mname, String type) {
        return clName.equals("java.lang.Object") && mname.equals("<init>") && type.equals("()V");
    }

    private boolean moveObjectSuperCall(CodeIterator it, int index, ConstPool cp) throws BadBytecode {
        //code.addAload(0);
        //code.addInvokespecial("java/lang/Object", MethodInfo.nameInit, "()V");
        if (it.byteAt(index - 1) == Opcode.ALOAD_0 &&
                it.byteAt(index) == Opcode.INVOKESPECIAL) {

            int methodInfoIndex = it.byteAt(index + 1);
            methodInfoIndex = methodInfoIndex << 8;
            methodInfoIndex = methodInfoIndex | it.byteAt(index + 2);
            if (isObjectSuper(cp.getMethodrefClassName(methodInfoIndex),
                    cp.getMethodrefName(methodInfoIndex),
                    cp.getMethodrefType(methodInfoIndex))) {


                int i1 = it.byteAt(index - 1);
                int i2 = it.byteAt(index);
                int i3 = it.byteAt(index + 1);
                int i4 = it.byteAt(index + 2);

                it.writeByte(Opcode.NOP, index - 1);
                it.writeByte(Opcode.NOP, index);
                it.writeByte(Opcode.NOP, index + 1);
                it.writeByte(Opcode.NOP, index + 2);

                it.begin();
                int pos = it.insertGap(4);
                it.writeByte(i1, pos);
                it.writeByte(i2, pos + 1);
                it.writeByte(i3, pos + 2);
                it.writeByte(i4, pos + 3);

                it.move(index + 4);
                return true;

            }
        }
        return false;

    }

    @Override
    public int transform(CtClass tclazz, int pos, CodeIterator iterator,
                         ConstPool cp) throws BadBytecode {
        // jump over all instructions before super or this calls.
        // field accesses can happen before super in compiled code (java moves these instructions),
        // so not doing this can lead to a method call injection before super or this,
        // what leads to passing uninitializedThis to method call, what is not allowed
        if (methodInfo.isConstructor() && !skippedConstCall) {
            int onCallIndex = iterator.skipConstructor();
            if (onCallIndex != -1) {
                // jumping over super or this call, pos is after
                // we move super calls to java.lang.Object to the beginning of the constructor
                // to support more constructors. We can do this for that case because this constructor call
                // has no side effects. Doing this in general is not possible this way and not trivial.
                if (this.moveObjectSuperCall(iterator, onCallIndex, cp)) {
                    iterator.begin();
                    pos = 0;
                } else {
                    iterator.move(onCallIndex);
                    pos = iterator.next();
                }
            }
            skippedConstCall = true;
        }
        return pos;
    }
}
